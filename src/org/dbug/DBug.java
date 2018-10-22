package org.dbug;

import java.util.function.Function;

import org.dbug.impl.DefaultDBug;

public class DBug {
	private static final DBugImplementation IMPLEMENTATION;

	static {
		String className = System.getProperty(DBug.class.getName() + ".impl");
		DBugImplementation implementation;
		if (className != null) {
			try {
				implementation = Class.forName(className).asSubclass(DBugImplementation.class).newInstance();
			} catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
				System.err.println("Could not create custom " + DBugImplementation.class.getName() + " " + className);
				e.printStackTrace();
				implementation = new DefaultDBug();
			}
		} else
			implementation = new DefaultDBug();
		IMPLEMENTATION = implementation;
	}

	public static <T> DBugAnchorType<T> declare(String schema, Class<T> type, Function<DBugAnchorTypeBuilder<T>, ?> builder) {
		return IMPLEMENTATION.declare(schema, type, b -> builder.apply(b));
	}

	public static void queueAction(Runnable action) {
		IMPLEMENTATION.queueAction(action);
	}

	private DBug() {
		throw new IllegalStateException("Not instantiable");
	}
}
