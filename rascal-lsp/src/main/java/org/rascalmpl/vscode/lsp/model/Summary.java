/** 
 * Copyright (c) 2018, Davy Landman, SWAT.engineering BV
 * All rights reserved. 
 *  
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met: 
 *  
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer. 
 *  
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution. 
 *  
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. 
 */
package org.rascalmpl.vscode.lsp.model;

import java.util.Map.Entry;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.rascalmpl.values.IRascalValueFactory;
import org.rascalmpl.values.ValueFactoryFactory;
import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.ISet;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.ITuple;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.IWithKeywordParameters;
import io.usethesource.vallang.type.Type;
import io.usethesource.vallang.type.TypeFactory;
import io.usethesource.vallang.type.TypeStore;

public class Summary {
	private final IConstructor summary;
	private final IWithKeywordParameters<? extends IConstructor> summaryKW;
	private static final ISet EMPTY_SET = ValueFactoryFactory.getValueFactory().setWriter().done();
	
	public Summary() {
		TypeFactory TF = TypeFactory.getInstance();
		TypeStore TS = new TypeStore();
		
		Type Summary = TF.abstractDataType(TS, "Summary");
		Type summary = TF.constructor(TS, Summary, "summary");
		this.summary = IRascalValueFactory.getInstance().constructor(summary);
		this.summaryKW = this.summary.asWithKeywordParameters();
	}

	public Summary(IConstructor summary) {
		assert summary != null && summary.getType().getName().equals("LSPSummary");
		this.summary = summary;
		this.summaryKW = summary.asWithKeywordParameters();
	}

	
	public Stream<Entry<ISourceLocation, ISourceLocation>> getDefinitions() {
		return toEntryStream(getKWFieldSet("definition"));
	}

	public Stream<Entry<ISourceLocation, ISourceLocation>> getTypeDefinitions() {
		return toEntryStream(getKWFieldSet("typeDefinition"));
	}
	
	public Stream<IConstructor> getDiagnostics() {
		return toStream(getKWFieldSet("diagnostics"));
	}
	
	private ISet getKWFieldSet(String name) {
		if (summaryKW.hasParameter(name)) {
			return (ISet) summaryKW.getParameter(name);
		}
		return EMPTY_SET;
	}
	
	@SuppressWarnings("unchecked")
	private static <T> Stream<T> toStream(ISet target) {
		return StreamSupport.stream(target.spliterator(), false)
			.map(v -> (T)v);
	}

	@SuppressWarnings("unchecked")
	private static <K,V> Stream<Entry<K,V>> toEntryStream(ISet target) {
		return Summary.<ITuple>toStream(target)
				.map(t -> new OwnEntry<>((K)t.get(0), (V)t.get(1)) );
	}

	private static final class OwnEntry<K,V> implements Entry<K,V> {
		
		private final K key;
		private final V value;
		
		public OwnEntry(K key, V value) {
			this.key = key;
			this.value = value;
		}


		@Override
		public K getKey() {
			return key;
		}

		@Override
		public V getValue() {
			return value;
		}

		@Override
		public V setValue(V value) {
			throw new RuntimeException("Not supported");
		}
		
	}

	public IValue getRascalSummary() {
		return summary;
	}
}