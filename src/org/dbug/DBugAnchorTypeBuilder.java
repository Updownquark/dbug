package org.dbug;

import java.util.function.Consumer;
import java.util.function.Function;

import com.google.common.reflect.TypeToken;

public interface DBugAnchorTypeBuilder<T> {
	<P> DBugAnchorTypeBuilder<T> withStaticField(String name, TypeToken<P> type, Function<? super T, ? extends P> value);
	<P> DBugAnchorTypeBuilder<T> withExternalStaticField(String name, TypeToken<P> type);

	<P> DBugAnchorTypeBuilder<T> withDynamicField(String name, TypeToken<P> type, Function<? super T, ? extends P> value);
	<P> DBugAnchorTypeBuilder<T> withExternalDynamicField(String name, TypeToken<P> type);

	DBugAnchorTypeBuilder<T> withEvent(String eventName, Consumer<DBugEventTypeBuilder> eventBuilder);
}
