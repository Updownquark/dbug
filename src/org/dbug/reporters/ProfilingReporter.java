package org.dbug.reporters;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.regex.Matcher;

import org.dbug.config.DBugConfig.DBugEventConfig;
import org.dbug.config.DBugConfig.DBugEventValue;
import org.dbug.config.DBugConfigEvent;
import org.dbug.config.SimpleDBugEventReporter;
import org.dbug.expression.ConstantExpression;
import org.observe.util.TypeTokens;
import org.qommons.QommonsUtils;
import org.qommons.Transaction;
import org.qommons.collect.BetterHashMap;
import org.qommons.collect.QuickSet;
import org.qommons.collect.QuickSet.QuickMap;
import org.qommons.config.QommonsConfig;

public class ProfilingReporter implements SimpleDBugEventReporter {
	private static final Object COUNT_PLACEHOLDER = new Object() {
		@Override
		public String toString() {
			return "${count}";
		}
	};
	private static final Object DURATION_PLACEHOLDER = new Object() {
		@Override
		public String toString() {
			return "${duration}";
		}
	};
	private static final Object INTRINSIC_DURATION_PLACEHOLDER = new Object() {
		@Override
		public String toString() {
			return "${intrinsicDuration}";
		}
	};
	private static final Object ACTIVE_DURATION_PLACEHOLDER = new Object() {
		@Override
		public String toString() {
			return "${activeDuration}";
		}
	};

	private long theStartTime;
	private long thePrintInterval;
	private long theForcePrintInterval;
	private long theResetInterval;
	private String theIndent = "\t";
	final Map<QuickMap<String, Object>, ProfileNode> theRoots;
	private final ThreadLocal<ProfilingThread> theUnfinishedStacks;
	private final BetterHashMap<DBugEventConfig<?>, EventProfileConfig> theEventDescrip;
	private volatile long theLastPrint;
	private long theLastReset;

	public ProfilingReporter() {
		theRoots = new LinkedHashMap<>();
		theUnfinishedStacks = ThreadLocal.withInitial(ProfilingThread::new);
		theEventDescrip = BetterHashMap.build().identity().buildMap();
	}

	@Override
	public void configure(QommonsConfig config) {
		thePrintInterval = config.getTime("print-interval", 0);
		theForcePrintInterval = config.getTime("force-print-interval", 0);
		theResetInterval = config.getTime("reset-interval", 0);
		if (config.get("indent") != null)
			theIndent = config.get("indent");

		theLastPrint = theLastReset = theStartTime = System.currentTimeMillis();
	}

	@Override
	public void eventOccurred(DBugConfigEvent<?> event) {
		// We don't care about instantaneous events--nothing to profile
		// TODO Maybe one day we can gather information on the time between events like this?
	}

	@Override
	public Transaction eventBegun(DBugConfigEvent<?> event) {
		ProfilingThread pt=theUnfinishedStacks.get();
		Transaction nodeT= pt.eventBegun(event,
			theEventDescrip.computeIfAbsent(event.getEventConfig(), ProfilingReporter::buildEventDescrip));
		return ()->{
			ProfileNode node = pt.theStack.removeLast();
			nodeT.close();
			boolean finished = pt.theStack.isEmpty();
			if (finished) {
				synchronized (this) { // Grab the lock now so we don't have to grab it twice
					mergeFinished(node);
					maybePrint(true);
				}
			} else
				maybePrint(false);
		};
	}

	@Override
	public void close() {
	}

	private void mergeFinished(ProfileNode node) {
		ProfileNode toMerge = theRoots.computeIfAbsent(node.group, g -> node);
		if (toMerge != node)
			toMerge.merge(node);
	}

	private void maybePrint(boolean finished) {
		long now=System.currentTimeMillis();
		long lastPrint=theLastPrint;
		long sinceLastPrint=now-lastPrint;
		if ((theForcePrintInterval > 0 && sinceLastPrint >= theForcePrintInterval)//
			|| (thePrintInterval > 0 && finished && sinceLastPrint >= thePrintInterval)) {
			synchronized(this){
				if(lastPrint!=theLastPrint){
					//Someone else printed; I don't have to now
					return;
				}
				now = System.currentTimeMillis(); // Can't use the value we got before because it may have taken time to get the lock
				theLastPrint = now;
				StringBuilder str=new StringBuilder();
				boolean reset = theResetInterval > 0 && (now - theLastReset) >= theResetInterval;
				if (reset)
					theLastReset = now;

				Iterator<ProfileNode> nodes=theRoots.values().iterator();
				while(nodes.hasNext()){
					ProfileNode node=nodes.next();
					NodeSnapshot snapshot=node.snapshot(now);
					snapshot.print(str, 0, theIndent);
					if(reset && snapshot.activeCount==0)
						theRoots.compute(node.group, (g, n)->{
							if(n.theActivity.get().count==0)
								return null;
							n.clearFinished();
							return n;
						});
				}
				System.out.println(str);
			}
		}
	}

	static EventProfileConfig buildEventDescrip(DBugEventConfig<?> eventConfig) {
		DBugEventValue<?, ?> profileConfigVar = eventConfig.eventValues.get("profile-grouping");
		if (profileConfigVar == null)
			return EventProfileConfig.EMPTY;
		else if (!(profileConfigVar.expression instanceof ConstantExpression)) {
			System.err.println(
				ProfilingReporter.class.getSimpleName() + " profile-grouping requires a constant value: " + profileConfigVar.expression);
			return EventProfileConfig.EMPTY;
		} else if (!TypeTokens.get().STRING.isAssignableFrom(profileConfigVar.expression.getResultType())) {
			System.err.println(ProfilingReporter.class.getSimpleName() + " profile-grouping requires a string-typed value, not "
				+ profileConfigVar.expression.getResultType() + " : " + profileConfigVar.expression);
			return EventProfileConfig.EMPTY;
		}
		String groupingString = ((ConstantExpression<?, String>) profileConfigVar.expression).value;
		Matcher m = SystemPrintReporter.PRINT_VAL_REF.matcher(groupingString);
		Map<String, Function<DBugConfigEvent<?>, Object>> map = new HashMap<>();
		ArrayList<Object> groupingPrint = new ArrayList<>();
		int lastEnd = 0;
		while (m.find()) {
			int start = m.start();
			if (lastEnd < start)
				groupingPrint.add(groupingString.substring(lastEnd, start));
			String name = m.group("name");
			if (name.equals("count"))
				groupingPrint.add(COUNT_PLACEHOLDER);
			else if (name.equals("duration"))
				groupingPrint.add(DURATION_PLACEHOLDER);
			else if (name.equals("intrinsicDuration"))
				groupingPrint.add(INTRINSIC_DURATION_PLACEHOLDER);
			else if (name.equals("activeDuration"))
				groupingPrint.add(ACTIVE_DURATION_PLACEHOLDER);
			else {
				Function<DBugConfigEvent<?>, Object> getter = getReference(eventConfig, name);
				if (getter == null)
					groupingPrint.add(m.group());
				else {
					map.put(name, getter);
					// Add a placeholder that will be replaced with the index into the parameter map
					groupingPrint.add(new String[] { name });
				}
			}
			lastEnd = m.end();
		}
		if (lastEnd < groupingString.length())
			groupingPrint.add(groupingString.substring(lastEnd));
		QuickMap<String, Function<DBugConfigEvent<?>, Object>> groupValues = QuickSet.of(map.keySet()).createMap();
		for (int i = 0; i < groupingPrint.size(); i++) {
			if (groupingPrint.get(i) instanceof String[]) {
				String valName = ((String[]) groupingPrint.get(i))[0];
				int index = groupValues.keyIndex(valName);
				groupingPrint.set(i, index);
				groupValues.put(index, map.get(valName));
			}
		}
		return new EventProfileConfig(groupingPrint, groupValues.unmodifiable());
	}

	private static Function<DBugConfigEvent<?>, Object> getReference(DBugEventConfig<?> eventConfig, String valName) {
		if (valName.equals("value"))
			return event -> event.getAnchor().getValue();
		else if (valName.equals("event"))
			return event -> event.getType().getEventName();
		int index = eventConfig.eventValues.keySet().indexOf(valName);
		if (index >= 0) {
			int fIndex = index;
			return event -> event.getEventConfigValues().get(fIndex);
		}
		index = eventConfig.eventType.getEventFields().keySet().indexOf(valName);
		if (index >= 0) {
			int fIndex = index;
			return event -> event.getEventValues().get(fIndex);
		}
		index = eventConfig.getConfig().getValues().keySet().indexOf(valName);
		if (index >= 0) {
			int fIndex = index;
			return event -> event.getAnchor().getConfigValues().get(fIndex);
		}
		index = eventConfig.eventType.getAnchorType().getStaticFields().keySet().indexOf(valName);
		if (index >= 0) {
			int fIndex = index;
			return event -> event.getAnchor().getStaticValues().get(fIndex);
		}
		index = eventConfig.eventType.getAnchorType().getDynamicFields().keySet().indexOf(valName);
		if (index >= 0) {
			int fIndex = index;
			return event -> event.getAnchor().getDynamicValues().get(fIndex);
		}
		return null;
	}

	static void printDuration(Duration d, StringBuilder str){
		QommonsUtils.printTimeLength(d.getSeconds(), d.getNano(), str, false);
	}

	private static class EventProfileConfig {
		static final EventProfileConfig EMPTY = new EventProfileConfig(Collections.emptyList(), QuickSet.<String> empty().createMap());

		final List<Object> groupingPrint;
		final QuickMap<String, Function<DBugConfigEvent<?>, Object>> groupValues;

		EventProfileConfig(List<Object> groupingPrint, QuickMap<String, Function<DBugConfigEvent<?>, Object>> groupValues) {
			this.groupingPrint = groupingPrint;
			this.groupValues = groupValues;
		}
	}

	private class ProfileNode {
		final EventProfileConfig theConfig;
		final QuickMap<String, Object> group;
		final AtomicLong theCount;
		final AtomicReference<Duration> theDuration;
		final AtomicReference<NodeActivity> theActivity;

		private final ConcurrentHashMap<QuickMap<String, Object>, ProfileNode> theChildren;

		ProfileNode(EventProfileConfig config, QuickMap<String, Object> group) {
			theConfig = config;
			this.group = group;
			theCount = new AtomicLong();
			theDuration = new AtomicReference<>(Duration.ZERO);
			theActivity = new AtomicReference<ProfilingReporter.NodeActivity>(new NodeActivity(0, 0));
			theChildren = new ConcurrentHashMap<>();
		}

		long getCount() {
			return theCount.get();
		}

		Duration getDuration() {
			return theDuration.get();
		}

		ProfileNode within(EventProfileConfig config, QuickMap<String, Object> childGroup) {
			return theChildren.computeIfAbsent(childGroup, g -> new ProfileNode(config, g));
		}

		void merge(ProfileNode other) {
			theCount.addAndGet(other.theCount.get());
			theDuration.accumulateAndGet(other.theDuration.get(), Duration::plus);
		}

		Transaction begin(DBugConfigEvent<?> event) {
			long now = System.currentTimeMillis();
			NodeActivity oldActivity = theActivity.get();
			NodeActivity newActivity = oldActivity.with(now);
			while (!theActivity.compareAndSet(oldActivity, newActivity)) {
				oldActivity = theActivity.get();
				newActivity = oldActivity.with(now);
			}
			NodeActivity fOld = oldActivity;
			NodeActivity fNew = newActivity;
			return () -> {
				theCount.getAndIncrement();
				theDuration.accumulateAndGet(Duration.between(event.getStart(), event.getEnd()), Duration::plus);
				if (!theActivity.compareAndSet(fNew, fOld))
					theActivity.getAndUpdate(activity -> activity.without(now));
			};
		}

		NodeSnapshot snapshot(long now) {
			LinkedList<NodeSnapshot> children = new LinkedList<>();
			for (ProfileNode node : theChildren.values())
				children.add(node.snapshot(now));
			return new NodeSnapshot(this, now, children);
		}

		void clearFinished(){
			theDuration.set(Duration.ZERO);
			theCount.set(0);
			for(ProfileNode n : theChildren.values())
				n.clearFinished();
		}
	}

	private static class NodeActivity {
		final int count;
		final long startTimeSum;

		NodeActivity(int count, long startTimeSum) {
			this.count = count;
			this.startTimeSum = startTimeSum;
		}

		NodeActivity with(long start) {
			return new NodeActivity(count + 1, startTimeSum + start);
		}

		NodeActivity without(long start) {
			return new NodeActivity(count - 1, startTimeSum - start);
		}
	}

	private class NodeSnapshot {
		final ProfileNode node;
		final long count;
		final int activeCount;
		final Duration duration;
		final Duration activeDuration;
		final Duration intrinsicDuration;
		final List<NodeSnapshot> children;

		public NodeSnapshot(ProfileNode node, long now, LinkedList<NodeSnapshot> children) {
			this.node = node;
			count = node.theCount.get();
			NodeActivity activity = node.theActivity.get();
			activeCount = activity.count;
			activeDuration = Duration.ofMillis((now - theStartTime) * activity.count - activity.startTimeSum);
			duration = node.theDuration.get().plus(activeDuration);
			Duration childDuration = Duration.ZERO;
			for (NodeSnapshot child : children) {
				childDuration = childDuration.plus(child.duration);
			}
			this.intrinsicDuration = duration.minus(childDuration);
			this.children = children;
		}

		void print(StringBuilder str, int indentAmount, String indent) {
			for (int i = 0; i < indentAmount; i++)
				str.append(indent);
			for (Object o : node.theConfig.groupingPrint) {
				if (o instanceof String)
					str.append((String) o);
				else if (o instanceof Integer)
					str.append(node.group.get(((Integer) o).intValue()));
				else if (o == COUNT_PLACEHOLDER)
					str.append(count);
				else if (o == DURATION_PLACEHOLDER)
					printDuration(duration, str);
				else if (o == INTRINSIC_DURATION_PLACEHOLDER)
					printDuration(intrinsicDuration, str);
				else if (o == ACTIVE_DURATION_PLACEHOLDER)
					printDuration(activeDuration, str);
			}
			str.append('\n');
			for (NodeSnapshot child : children)
				child.print(str, indentAmount + 1, indent);
		}
	}

	private class ProfilingThread {
		final LinkedList<ProfileNode> theStack;

		ProfilingThread() {
			theStack = new LinkedList<>();
		}

		Transaction eventBegun(DBugConfigEvent<?> event, EventProfileConfig config) {
			QuickMap<String, Object> group = config.groupValues.keySet().createMap();
			for (int i = 0; i < group.keySet().size(); i++)
				group.put(i, config.groupValues.get(i).apply(event));
			if (theStack.isEmpty()) {
				ProfileNode root = theRoots.computeIfAbsent(group, g -> new ProfileNode(config, g));
				theStack.add(root);
				return root.begin(event);
			} else {
				ProfileNode child = theStack.getLast().within(config, group);
				theStack.add(child);
				return child.begin(event);
			}
		}
	}
}
