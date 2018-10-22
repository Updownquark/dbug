package org.dbug;

import java.util.function.Function;

import com.google.common.reflect.TypeToken;

public class DBugParameterType<T, P> {
	public final TypeToken<P> type;
	public final Function<? super T, ? extends P> producer;
	public final DBugFieldType level;

	public DBugParameterType(TypeToken<P> type, Function<? super T, ? extends P> producer, DBugFieldType level) {
		this.type = type;
		this.producer = producer;
		this.level = level;
	}
}