package org.rascalmpl.vscode.lsp;

import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;

public interface IRascalFileSystemService {
    @JsonRequest("rascal/filesystem/schemes")
    String[] fileSystemSchemes();

    @JsonRequest("rascal/filesystem/watch")
    void watch(String uri, boolean recursive, String[] excludes);

    @JsonNotification("rascal/filesystem/onDidChangeFile")
    void onDidChangeFile(FileChangeEvent event);

    @JsonRequest("rascal/filesystem/stat")
    FileStat stat(String uri);

    @JsonRequest("rascal/filesystem/readDirectory")
    FileWithType[] readDirectory(String uri);

    @JsonRequest("rascal/filesystem/createDirectory")
    void createDirectory(String uri);

    @JsonRequest("rascal/filesystem/readFile")
    String readFile(String uri);

    @JsonRequest("rascal/filesystem/writeFile")
    void writeFile(String uri, String content, boolean create, boolean overwrite);

    @JsonRequest("rascal/filesystem/delete")
    void delete(String uri, boolean recursive);

    @JsonRequest("rascal/filesystem/rename")
    void rename(String oldUri, String newUri, boolean overwrite);

    public static class FileChangeEvent {
        FileChangeType type;
        String uri;

        public FileChangeEvent(FileChangeType type, String uri) {
            this.type = type;
            this.uri = uri;
        }
    }

    public static enum FileChangeType {
        Changed(1),
        Created(2),
        Deleted(3);
    }

    public static class FileStat {
        FileType type;
        long ctime;
        long mtime;
        long size;
    }

    public static enum FileType {
        Unknown(0),
        File(1),
        Directory(2),
        SymbolicLink(64);
    }

    public static class FileWithType {
        String name;
        FileType type;
    }
}
