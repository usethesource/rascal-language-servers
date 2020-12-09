package org.rascalmpl.vscode.lsp.util;

public class StampedReference<T> {
	final T value;
	final long stamp;

	public StampedReference(T ref, long stamp) {
		this.value = ref;
		this.stamp = stamp;
	}
}