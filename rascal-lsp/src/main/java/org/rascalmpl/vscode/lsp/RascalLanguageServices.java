/** 
 * Copyright (c) 2018, Davy Landman, SWAT.engineering BV, 2020 Jurgen J. Vinju, NWO-I CWI
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
package org.rascalmpl.vscode.lsp;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.core.runtime.ICoreRunnable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.rascalmpl.debug.IRascalMonitor;
import org.rascalmpl.exceptions.Throw;
import org.rascalmpl.interpreter.Evaluator;
import org.rascalmpl.interpreter.control_exceptions.InterruptException;
import org.rascalmpl.library.lang.rascal.syntax.RascalParser;
import org.rascalmpl.library.util.PathConfig;
import org.rascalmpl.parser.Parser;
import org.rascalmpl.parser.gtd.io.InputConverter;
import org.rascalmpl.parser.gtd.result.action.IActionExecutor;
import org.rascalmpl.parser.gtd.result.out.DefaultNodeFlattener;
import org.rascalmpl.parser.uptr.UPTRNodeFactory;
import org.rascalmpl.parser.uptr.action.NoActionExecutor;
import org.rascalmpl.shell.ShellEvaluatorFactory;
import org.rascalmpl.uri.URIResolverRegistry;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.values.IRascalValueFactory;
import org.rascalmpl.values.parsetrees.ITree;
import org.rascalmpl.values.parsetrees.TreeAdapter;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.IMap;
import io.usethesource.vallang.INode;
import io.usethesource.vallang.ISet;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IString;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.IValueFactory;
import io.usethesource.vallang.IWithKeywordParameters;

public class RascalLanguageServices {
    private static final IValueFactory vf = IRascalValueFactory.getInstance();
    
    private final Cache<ISourceLocation, IConstructor> summaryCache;
    private final Cache<ISourceLocation, INode> outlineCache;
    
    private final Future<Evaluator> outlineEvaluator = makeFutureEvaluator("Loading Rascal outline evaluator", "lang::rascal::ide::Outline");
    private final Future<Evaluator> summaryEvaluator = makeFutureEvaluator("Loading Rascal summary evaluator", "lang::rascalcore::check::Summary");
    private final Future<Evaluator> compilerEvaluator = makeFutureEvaluator("Loading Rascal compiler evaluator", "lang::rascalcore::check::Checker");
    
    private RascalLanguageServices() {
            summaryCache = Caffeine.newBuilder()
            		.softValues()
            		.maximumSize(256)
            		.expireAfterAccess(60, TimeUnit.MINUTES)
            		.build();

            outlineCache = Caffeine.newBuilder()
            		.softValues()
            		.expireAfterWrite(60, TimeUnit.MINUTES)
            		.maximumSize(512)
            		.build();
    }

    private static class InstanceHolder {
        static RascalLanguageServices sInstance = new RascalLanguageServices();
    }
    
    public static RascalLanguageServices getInstance() {
        return InstanceHolder.sInstance;
    }
    
    private synchronized <T extends IValue> T get(ISourceLocation occ, PathConfig pcfg, String field, T def) {
       IConstructor summary = getSummary(occ, pcfg);
       
       if (summary != null) {
           IWithKeywordParameters<? extends IConstructor> kws = summary.asWithKeywordParameters();
           if (kws.hasParameters()) {
               @SuppressWarnings("unchecked")
               T val = (T) kws.getParameter(field);
               
               if (val != null) {
                   return val;
               }
           }
       }
       
       return def;
    }
    
    public synchronized IConstructor getSummary(ISourceLocation occ, PathConfig pcfg) {
    	return summaryCache.get(occ.top(), (u) -> {
    		try {
    		    Evaluator eval = summaryEvaluator.get();
    		    
                if (eval == null) {
                    Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).log(Level.WARNING, "Could not calculate summary due to missing evaluator");
                    return null;
                }
                
                synchronized (eval) {
                    IConstructor result = (IConstructor) eval.call("makeSummary", vf.string(pcfg.getModuleName(occ)), pcfg.asConstructor());

                    return result != null && result.asWithKeywordParameters().hasParameters() ? result : null;
                }
    		}
    		catch (Throwable e) {
                Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).log(Level.WARNING, "failure to create summary for IDE features", e);
    			return null;
    		}
    	});
     }
    
    public IList compileFolder(IRascalMonitor monitor, ISourceLocation folder, PathConfig pcfg) {
        try {
            Evaluator eval = compilerEvaluator.get();
            
            synchronized (eval) {
                try {
                    return (IList) eval.call(monitor, "checkAll", folder, pcfg.asConstructor());
                }
                catch (InterruptException e) {
                    return IRascalValueFactory.getInstance().list();
                }
                finally {
                    eval.__setInterrupt(false);
                }
            }
        } catch (InterruptedException | ExecutionException e) {
            Logger.getGlobal().log(Level.SEVERE, "compilation failed", e);
            return IRascalValueFactory.getInstance().list();
        } catch (Throw e) {
            Logger.getGlobal().log(Level.SEVERE, "internal error during compilation;\n" + e.getLocation() + ": " + e.getMessage() + "\n" + e.getTrace(), e);
            return IRascalValueFactory.getInstance().list();
        } catch (Throwable e) {
            Logger.getGlobal().log(Level.SEVERE, "compilation failed", e);
            return IRascalValueFactory.getInstance().list();
        }
    }
    
    public IList compileFileList(IRascalMonitor monitor, IList files, PathConfig pcfg) {
        try {
            Evaluator eval = compilerEvaluator.get();
           
            synchronized (eval) {
                try {
                    return (IList) eval.call(monitor, "check", files, pcfg.asConstructor());
                }
                catch (InterruptException e) {
                    return IRascalValueFactory.getInstance().list();
                }
                finally {
                    eval.__setInterrupt(false);
                }
            }
        } catch (InterruptedException | ExecutionException e) {
            Logger.getGlobal().log(Level.SEVERE, "compilation failed", e);
            return IRascalValueFactory.getInstance().list();
        } catch (Throw e) {
            Logger.getGlobal().log(Level.SEVERE, "internal error during compilation;\n" + e.getLocation() + ": " + e.getMessage() + "\n" + e.getTrace(), e);
            return IRascalValueFactory.getInstance().list();
        } catch (Throwable e) {
            Logger.getGlobal().log(Level.SEVERE, "compilation failed", e);
            return IRascalValueFactory.getInstance().list();
        }
    }
    
    public ISet getUseDef(ISourceLocation file, PathConfig pcfg, String moduleName) {
        return get(file, pcfg, "useDef", vf.set());
    }
    
    public IString getType(ISourceLocation occ, PathConfig pcfg){
    	IMap locationTypes = get(occ, pcfg, "locationTypes", vf.mapWriter().done());
    	return (IString) locationTypes.get(occ);
    }
    
    public ISet getDefs(ISourceLocation occ, PathConfig pcfg) {
        ISet useDefs = get(occ, pcfg, "useDef", vf.set());
        return useDefs.asRelation().index(occ);
    }
    
    public IString getSynopsis(ISourceLocation occ, PathConfig pcfg) {
        IMap synopses = get(occ, pcfg, "synopses", vf.mapWriter().done());
        return (IString) synopses.get(occ);
    }
    
    public ISourceLocation getDocLoc(ISourceLocation occ, PathConfig pcfg) {
        IMap docLocs = get(occ, pcfg, "docLocs", vf.mapWriter().done());
        return (ISourceLocation) docLocs.get(occ);
    }
    
    private ISourceLocation getFileLoc(ITree moduleTree) {
    	try {
    		if (TreeAdapter.isTop(moduleTree)) {
    			moduleTree = TreeAdapter.getStartTop(moduleTree);
    		}
    		ISourceLocation loc = TreeAdapter.getLocation(moduleTree);
    		if (loc != null) {
    			return loc.top();
    		}
    		return null;
    	} catch (Throwable t) {
    		return null;
    	}
    }
    
    private final static INode EMPTY_NODE = vf.node("");

    private INode replaceNull(@Nullable INode result) {
    	return result == null ? EMPTY_NODE : result;
	}

    public INode getOutline(IConstructor module) {
        ISourceLocation loc = getFileLoc((ITree) module);
        if (loc == null) {
            return EMPTY_NODE;
        }

        return replaceNull(outlineCache.get(loc.top(), (l) -> {
            try {
                Evaluator eval = outlineEvaluator.get();

                if (eval == null) {
                    Logger.getGlobal().log(Level.SEVERE, "Could not calculate outline due to missing evaluator");
                    return null;
                }

                synchronized (eval) {
                    return (INode) eval.call("outline", module);
                }
            }
            catch (Throwable e) {
                Logger.getGlobal().log(Level.SEVERE, "failure to create outline");
                return null;
            }
        }));
    }

	public void clearSummaryCache(ISourceLocation file) {
        summaryCache.invalidate(file.top());
        outlineCache.invalidate(file.top());
    }

    public void invalidateEverything() {
        summaryCache.invalidateAll();
        outlineCache.invalidateAll();;
    }
    
    public PathConfig getProjectPathConfig(File prj) {
        if (prj != null) {
            // TODO; make sure we get the right PathConfig for this file location
            return new PathConfig();
        }
    	
    	return new PathConfig();
    }
    
    public PathConfig getModulePathConfig(ISourceLocation module) {
        if (module.getScheme().equals("lib")) {
            try {
                return PathConfig.fromLibraryRascalManifest(module.getAuthority());
            }
            catch (IOException e) {
                Logger.getGlobal().log(Level.SEVERE, "could not configure compiler for " + module, e);
            }
        }

        // TODO: resolve path config for this module location
        return new PathConfig();
    }
    
    private Future<Evaluator> makeFutureEvaluator(String label, final String... imports) {
        return asyncGenerator(label, () ->  {
            // TODO: channel the streams to the IDE
            Evaluator eval = ShellEvaluatorFactory.getDefaultEvaluator(System.in, System.out, System.err);
           
            eval.getConfiguration().setRascalJavaClassPathProperty("/Users/jurgenv/.m2/repository/org/rascalmpl/rascal/0.19.3-SNAPSHOT/rascal-0.19.3-SNAPSHOT.jar:/Users/jurgenv/.m2/repository/org/rascalmpl/rascal-core/0.4.17-SNAPSHOT/rascal-core-0.4.17-SNAPSHOT.jar:/Users/jurgenv/.m2/repository/junit/junit/4.12/junit-4.12.jar:/Users/jurgenv/.m2/repository/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar:/Users/jurgenv/.m2/repository/io/usethesource/vallang/0.12.0/vallang-0.12.0.jar:/Users/jurgenv/.m2/repository/io/usethesource/capsule/0.6.3/capsule-0.6.3.jar");
            eval.addClassLoader(RascalLanguageServer.class.getClassLoader());
            eval.addClassLoader(IValue.class.getClassLoader());
            eval.addRascalSearchPath(URIUtil.correctLocation("lib", "typepal", ""));
            eval.addRascalSearchPath(URIUtil.correctLocation("lib", "rascal-core", ""));
            
            for (String i : imports) {
                try {
                    eval.doImport(eval, i);
                }
                catch (Throwable e) {
                    Logger.getGlobal().log(Level.WARNING, "failed to import: " + i, e);
                }
            }
           
            return eval;
        });
    }
    
    private static <T> Future<T> asyncGenerator(String name, Callable<T> generate) {
        FutureTask<T> result = new FutureTask<>(() -> {
            try {
                return generate.call();
            } catch (Throwable e) {
                Logger.getGlobal().log(Level.SEVERE, "Cannot initialize " + name, e);
                return null;
            }
        });
        
        Job job = Job.create(name, new ICoreRunnable() {
            @Override
            public void run(IProgressMonitor monitor) {
                result.run();
            }
        });
        
        job.schedule();
        
        return result;
    }

    public ITree parseSourceFile(ISourceLocation loc, String input) {
        IActionExecutor<ITree> actions = new NoActionExecutor();
        return new RascalParser().parse(Parser.START_MODULE, loc.getURI(), input.toCharArray(), actions,
            new DefaultNodeFlattener<IConstructor, ITree, ISourceLocation>(), new UPTRNodeFactory(true));
    }

    public ITree parseSourceFile(ISourceLocation loc) throws IOException {
        IActionExecutor<ITree> actions = new NoActionExecutor();
        return new RascalParser().parse(Parser.START_MODULE, loc.getURI(), getResourceContent(loc), actions,
            new DefaultNodeFlattener<IConstructor, ITree, ISourceLocation>(), new UPTRNodeFactory(true));
    }

    private static final char[] getResourceContent(ISourceLocation location) throws IOException{
        char[] data;
        Reader textStream = null;
        
        URIResolverRegistry resolverRegistry = URIResolverRegistry.getInstance();
        try {
            textStream = resolverRegistry.getCharacterReader(location);
            data = InputConverter.toChar(textStream);
        }
        finally{
            if(textStream != null){
                textStream.close();
            }
        }
        
        return data;
    }
}
