package org.dbug;

public interface DBugAnchorBuilder<T> {
	DBugAnchorBuilder<T> with(String parameter, Object value);

	DBugAnchor<T> build();

	DBugAnchor<T> buildIfSatisfied();
}
