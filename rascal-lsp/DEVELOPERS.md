# Rascal LSP Developer Manual

This manual describes best pratices when developing the Rascal Language Server and Language Parametric Language Server.

## Futures

LSP uses a client/server architecture. LSP4J uses [`CompletableFuture`](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html) to implement asynchronous requests and reponses. By default, a newly created `CompletableFuture` runs on a thread from the 'common fork join pool'. Since threads from this pool should not be used for long-running tasks, and to allow for named threads in logs, Rascal LSP uses a number of dedicated thread pools (all instances of the custom class `NamedThreadPool`).

- The language client implementation, responsible for forwarding requests from VS Code to the server, uses a 'request' pool. Since the order of incoming LSP requests should be maintained, this pool only has a *single* thread, effectively dispatching requests sequentially.
- The services implementing the various LSP features (instances of `TextDocumentService` and `WorkspaceService`) use a thread pool with multiple threads, which enables doing computations in parallel and asynchronously. Thread pools for the Rascal and parametric server instances are completely separate, while all services (document/workspace) for a single server instance share a pool.

When writing code that uses futures, please adhere to the following guidelines.

- When creating a future that does some delayed work (e.g. by calling `CompletableFuture::runAsync` or `CompletableFuture::supplyAsync`), make sure to explicitly pass the thread pool for the server instance as the last argument. If not, the work will be performed on the common fork join pool.
- When creating a future that immediately completes (e.g. by calling `CompletableFuture::completedFuture`), instead use `CompletableFutureUtils::completedFuture` and pass the pass the thread pool for the server instance as the last argument. If not,  any chaining calls (e.g. `thenApply`, `thenCombine`, `thenCompose`) will be performed on the common fork join pool.
- When merging futures using `thenCompose` or `thenCombine`, where it is not guaranteed that both merged futures run on the right thread pool, use `thenComposeAsync`/`theCombineAsync` instead, and pass the thread pool for the server instance as the last argument.
