package org.rascalmpl.vscode.lsp.util;

public class TimedReference<T> {
	final T value;
	final long timestamp;

	public TimedReference(T ref, long timestamp) {
		this.value = ref;
		this.timestamp = timestamp;
	}
}