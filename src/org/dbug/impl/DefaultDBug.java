package org.dbug.impl;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLConnection;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.dbug.DBug;
import org.dbug.DBugAnchor;
import org.dbug.DBugAnchorBuilder;
import org.dbug.DBugAnchorType;
import org.dbug.DBugAnchorTypeBuilder;
import org.dbug.DBugImplementation;
import org.dbug.config.DBugConfig;
import org.dbug.config.DBugConfigTemplate;
import org.dbug.expression.DBugParseException;
import org.qommons.BiTuple;
import org.qommons.Transaction;
import org.qommons.collect.BetterSortedSet.SortedSearchFilter;
import org.qommons.collect.CollectionElement;
import org.qommons.config.QommonsConfig;
import org.qommons.tree.SortedTreeList;

public class DefaultDBug implements DBugImplementation {
	final static Comparator<DBugConfigTemplate> CONFIG_TEMPLATE_SORT = (c1, c2) -> {
		int comp = c1.getClassName().compareTo(c2.getClassName());
		if (comp == 0 && c1.getID() != c2.getID()) {
			if (c1.getID() == null)
				comp = 1;
			else if (c2.getID() == null)
				comp = -1;
			else
				comp = c1.getID().compareTo(c2.getID());
		}
		return comp;
	};

	private final DefaultDBugProcess theProcess;
	private final ConcurrentHashMap<String, List<? extends DefaultDBugAnchorType<?>>> theAnchorTypes;
	private final ConcurrentHashMap<StorageKey, DBugAnchorHolder> theAnchors;

	private final SortedTreeList<DBugConfigTemplate> theConfigs;
	private final AtomicLong theEventIdSequence;
	private final ConcurrentLinkedQueue<Runnable> theActionQueue;

	private final DBugConfigSet theConfig;
	private String theConfigString;
	private URL theConfigUrl;
	private String theConfigError;

	long lastConfigCheck;
	final long configCheckInterval = 1000;
	final String configProperty = DBug.class.getName() + ".config";

	public DefaultDBug() {
		theProcess = new DefaultDBugProcess();
		theAnchorTypes = new ConcurrentHashMap<>();
		theAnchors = new ConcurrentHashMap<>();
		theConfigs = new SortedTreeList<>(true, CONFIG_TEMPLATE_SORT);
		theEventIdSequence = new AtomicLong();
		theActionQueue = new ConcurrentLinkedQueue<>();
		theConfig = new DBugConfigSet();

		checkConfig();
		new Thread(() -> {
			while (true) {
				checkConfig();
				Runnable action = theActionQueue.poll();
				while (action != null) {
					action.run();
					action = theActionQueue.poll();
				}
				try {
					Thread.sleep(2);
				} catch (InterruptedException e) {}
			}
		}, getClass().getSimpleName() + " Reporting").start();
	}

	void checkConfig() {
		long now = System.currentTimeMillis();
		if (now >= lastConfigCheck + configCheckInterval) {
			// Check for config changes
			String config = System.getProperty(configProperty);
			URL configURL;
			long lastMod = -1;
			try {
				if (config != null) {
					configURL = QommonsConfig.toUrl(config);
					URLConnection conn = configURL.openConnection();
					lastMod = conn.getLastModified();
				} else
					configURL = null;
				if (!Objects.equals(theConfigUrl, configURL) || lastMod > lastConfigCheck) {
					theConfigUrl = configURL;
					theConfig.read(theConfigUrl, DefaultDBug.this);
				}
			} catch (IOException e) {
				if (!Objects.equals(theConfigString, config) || !Objects.equals(theConfigError, e.getMessage())) {
					System.err.println("Could not resolve, read, or parse " + configProperty + " value " + config);
					e.printStackTrace();
					theConfigError = e.getMessage();
				}
			} catch (DBugParseException | RuntimeException e) {
				if (!Objects.equals(theConfigString, config) || !Objects.equals(theConfigError, e.getMessage())) {
					System.err.println("Error initializing or updating config " + config);
					e.printStackTrace();
					theConfigError = e.getMessage();
				}
			} finally {
				lastConfigCheck = now;
				theConfigString = config;
			}
		}
	}

	public void addConfig(DBugConfigTemplate config, Consumer<String> onError) {
		_addConfig(config, onError);
	}

	private <T> void _addConfig(DBugConfigTemplate config, Consumer<String> onError) {
		long eventId = getNextEventId();
		theConfigs.add(config);
		Map<DBugAnchorType<T>, DBugConfig<T>> configs = new IdentityHashMap<>();
		theAnchorTypes.computeIfPresent(config.getClassName(), (k, anchorTypes) -> {
			for (DefaultDBugAnchorType<T> at : (List<DefaultDBugAnchorType<T>>) anchorTypes) {
				DBugConfig<T> cfg = at.addConfig(config, onError);
				if (cfg != null)
					configs.put(at, cfg);
			}
			return anchorTypes;
		});
		if (configs.isEmpty())
			return;
		Iterator<DBugAnchorHolder> anchors = theAnchors.values().iterator();
		while (anchors.hasNext()) {
			DefaultDBugAnchor<?> a = anchors.next().weakRef.get();
			if (a != null) {
				DBugConfig<T> cfg = configs.get(a.getType());
				if (cfg != null)
					((DefaultDBugAnchor<T>) a).addConfig(cfg, eventId);
			} else
				anchors.remove();
		}
	}

	public void removeConfig(DBugConfigTemplate config) {
		_removeConfig(config);
	}

	private <T> void _removeConfig(DBugConfigTemplate config) {
		if (!theConfigs.remove(config))
			return;
		Map<DBugAnchorType<T>, DBugConfig<T>> configs = new IdentityHashMap<>();
		theAnchorTypes.computeIfPresent(config.getClassName(), (k, anchorTypes) -> {
			for (DefaultDBugAnchorType<T> at : (List<DefaultDBugAnchorType<T>>) anchorTypes) {
				DBugConfig<T> cfg = at.removeConfig(config);
				if (cfg != null)
					configs.put(at, cfg);
			}
			return anchorTypes;
		});
		if (configs.isEmpty())
			return;
		Iterator<DBugAnchorHolder> anchors = theAnchors.values().iterator();
		while (anchors.hasNext()) {
			DefaultDBugAnchor<?> a = anchors.next().weakRef.get();
			if (a != null) {
				DBugConfig<T> cfg = configs.get(a.getType());
				if (cfg != null)
					((DefaultDBugAnchor<T>) a).removeConfig(cfg);
			} else
				anchors.remove();
		}
	}

	public void updateConfig(DBugConfigTemplate oldConfig, DBugConfigTemplate newConfig, Consumer<String> onError) {
		if (oldConfig.getClassName().equals(newConfig.getClassName())) {
			_updateConfig(oldConfig, newConfig, onError);
		} else {
			removeConfig(oldConfig);
			addConfig(newConfig, onError);
		}
	}

	private <T> void _updateConfig(DBugConfigTemplate oldConfig, DBugConfigTemplate newConfig, Consumer<String> onError) {
		CollectionElement<DBugConfigTemplate> el = theConfigs.getElement(oldConfig, true);
		if (el == null)
			return;
		theConfigs.mutableElement(el.getElementId()).set(newConfig);
		Map<DBugAnchorType<T>, BiTuple<DBugConfig<T>, DBugConfig<T>>> configs = new IdentityHashMap<>();
		theAnchorTypes.computeIfPresent(oldConfig.getClassName(), (k, anchorTypes) -> {
			for (DefaultDBugAnchorType<T> at : (List<DefaultDBugAnchorType<T>>) anchorTypes) {
				BiTuple<DBugConfig<T>, DBugConfig<T>> cfg = at.updateConfig(oldConfig, newConfig, onError);
				if (cfg != null)
					configs.put(at, cfg);
			}
			return anchorTypes;
		});
		if (configs.isEmpty())
			return;
		Iterator<DBugAnchorHolder> anchors = theAnchors.values().iterator();
		while (anchors.hasNext()) {
			DefaultDBugAnchor<?> a = anchors.next().weakRef.get();
			if (a != null) {
				BiTuple<DBugConfig<T>, DBugConfig<T>> cfg = configs.get(a.getType());
				if (cfg != null)
					((DefaultDBugAnchor<T>) a).updateConfig(cfg.getValue1(), cfg.getValue2());
			} else
				anchors.remove();
		}
	}

	public DefaultDBugProcess getProcess() {
		return theProcess;
	}

	public long getNextEventId() {
		return theEventIdSequence.getAndIncrement();
	}

	@Override
	public void queueAction(Runnable action) {
		theActionQueue.add(action);
	}

	@Override
	public <T> DBugAnchorType<T> declare(Class<T> type, Consumer<DBugAnchorTypeBuilder<T>> builder) {
		DefaultDBugAnchorType<T>[] ret = new DefaultDBugAnchorType[1];
		theAnchorTypes.compute(type.getName(), (k, ats) -> {
			List<DefaultDBugAnchorType<T>> anchorTypes = (List<DefaultDBugAnchorType<T>>) ats;
			if (anchorTypes == null) {
				ats = anchorTypes = new LinkedList<>();
			} else {
				for (DefaultDBugAnchorType<T> at : anchorTypes) {
					if (at.theBuilderClass.equals(builder.getClass())) {
						ret[0] = at;
						break;
					}
				}
			}
			if (ret[0] == null) {
				ret[0] = buildAnchorType(type, builder);
				anchorTypes.add(ret[0]);
			}
			return anchorTypes;
		});
		return ret[0];
	}

	private <T> DefaultDBugAnchorType<T> buildAnchorType(Class<T> clazz, Consumer<DBugAnchorTypeBuilder<T>> builder) {
		DefaultAnchorTypeBuilder<T> b = new DefaultAnchorTypeBuilder<>(clazz);
		builder.accept(b);
		DefaultDBugAnchorType<T> at = new DefaultDBugAnchorType<>(this, clazz, builder.getClass(), b.theValues, b.theEvents);
		try (Transaction t = theConfigs.lock(false, null)) {
			CollectionElement<DBugConfigTemplate> config = theConfigs.search(cfg -> clazz.getName().compareTo(cfg.getClassName()),
				SortedSearchFilter.PreferLess);
			if (config != null && config.get().getClassName().equals(clazz.getName())) {
				CollectionElement<DBugConfigTemplate> adj = theConfigs.getAdjacentElement(config.getElementId(), false);
				while (adj != null && adj.get().getClassName().equals(clazz.getName())) {
					config = adj;
					adj = theConfigs.getAdjacentElement(config.getElementId(), false);
				}
				do {
					at.addConfig(config.get(), err -> {
						System.err.println("Error on config template for anchor type " + clazz.getName() + ": " + err);
					});
					config = theConfigs.getAdjacentElement(config.getElementId(), true);
				} while (config != null && config.get().getClassName().equals(clazz.getName()));
			}
		}
		return at;
	}

	<T> DBugAnchor<T> getPreBuiltAnchor(DefaultDBugAnchorType<T> type, T value) {
		if (theConfigUrl == null || type.getConfigs().isEmpty())
			return type.inactive();
		DBugAnchorHolder holder = theAnchors.compute(new LightStorageKey(type, value), (k, h) -> {
			if (h == null || !h.hold())
				return null;
			return h;
		});
		if (holder != null)
			return holder.release();
		return null;
	}

	<T> DBugAnchorBuilder<T> debug(DefaultDBugAnchorType<T> type, T value) {
		if (theConfigUrl == null || type.getConfigs().isEmpty())
			return new PreResolvedAnchorBuilder<>(type.inactive());
		DBugAnchorHolder holder = theAnchors.compute(new LightStorageKey(type, value), (k, h) -> {
			if (h == null || !h.hold())
				return null;
			return h;
		});
		if (holder != null)
			return new PreResolvedAnchorBuilder<>(holder.release());
		return new DefaultAnchorBuilder<>(this, type, value);
	}

	<T> DefaultDBugAnchor<T> buildAnchor(DefaultDBugAnchorType<T> type, T value, Supplier<DefaultDBugAnchor<T>> supplier) {
		return theAnchors.compute(new WeakStorageKey(type, value), (k, h) -> {
			if (h == null || !h.hold())
				return new DBugAnchorHolder(supplier.get());
			else
				return h;
		}).release();
	}

	private static class PreResolvedAnchorBuilder<T> implements DBugAnchorBuilder<T> {
		private final DBugAnchor<T> theAnchor;

		PreResolvedAnchorBuilder(DBugAnchor<T> anchor) {
			theAnchor = anchor;
		}

		@Override
		public DBugAnchorBuilder<T> with(String parameter, Object value) {
			return this;
		}

		@Override
		public DBugAnchor<T> build() {
			return theAnchor;
		}

		@Override
		public DBugAnchor<T> buildIfSatisfied() {
			return theAnchor;
		}
	}

	private static abstract class StorageKey {
		final DBugAnchorType<?> theAnchorType;
		final int hashCode;

		StorageKey(DBugAnchorType<?> type, Object value) {
			theAnchorType = type;
			hashCode = type.hashCode() * 17 + System.identityHashCode(value);
		}

		@Override
		public int hashCode() {
			return hashCode;
		}

		protected abstract Object get();

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof StorageKey))
				return false;
			StorageKey other = (StorageKey) obj;
			if (hashCode != other.hashCode || theAnchorType != other.theAnchorType)
				return false;
			Object value = get();
			Object otherValue = other.get();
			if (value == null || otherValue == null)
				return false;
			return value == otherValue;
		}

		@Override
		public String toString() {
			Object value = get();
			if (value == null)
				return "(collected)";
			return value.toString();
		}
	}

	private static class LightStorageKey extends StorageKey {
		final Object theValue;

		LightStorageKey(DBugAnchorType<?> type, Object value) {
			super(type, value);
			theValue = value;
		}

		@Override
		protected Object get() {
			return theValue;
		}
	}

	private static class WeakStorageKey extends StorageKey {
		final WeakReference<Object> theValue;

		WeakStorageKey(DBugAnchorType<?> type, Object value) {
			super(type, value);
			theValue = new WeakReference<>(value);
		}

		@Override
		protected Object get() {
			return theValue.get();
		}
	}

	private static class DBugAnchorHolder {
		final WeakReference<DefaultDBugAnchor<?>> weakRef;
		DefaultDBugAnchor<?> strongRef;

		DBugAnchorHolder(DefaultDBugAnchor<?> anchor) {
			weakRef = new WeakReference<>(anchor);
			strongRef = anchor;
		}

		boolean hold() {
			if (strongRef == null)
				strongRef = weakRef.get();
			return strongRef != null;
		}

		<T> DefaultDBugAnchor<T> release() {
			DefaultDBugAnchor<T> value = (DefaultDBugAnchor<T>) strongRef;
			strongRef = null;
			return value;
		}
	}
}
