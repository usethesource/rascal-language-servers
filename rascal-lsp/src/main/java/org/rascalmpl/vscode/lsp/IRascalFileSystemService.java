package org.rascalmpl.vscode.lsp;

import java.io.IOException;
import java.net.URISyntaxException;

import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;

public interface IRascalFileSystemService {
    @JsonRequest("rascal/filesystem/schemes")
    String[] fileSystemSchemes();

    @JsonRequest("rascal/filesystem/watch")
    void watch(String uri, boolean recursive, String[] excludes);

    @JsonNotification("rascal/filesystem/onDidChangeFile")
    default void onDidChangeFile(FileChangeEvent event) { };

    @JsonRequest("rascal/filesystem/stat")
    FileStat stat(String uri) throws IOException, URISyntaxException;

    @JsonRequest("rascal/filesystem/readDirectory")
    FileWithType[] readDirectory(String uri) throws URISyntaxException, IOException;

    @JsonRequest("rascal/filesystem/createDirectory")
    void createDirectory(String uri) throws IOException, URISyntaxException;

    @JsonRequest("rascal/filesystem/readFile")
    String readFile(String uri) throws URISyntaxException;

    @JsonRequest("rascal/filesystem/writeFile")
    void writeFile(String uri, String content, boolean create, boolean overwrite) throws URISyntaxException, IOException;

    @JsonRequest("rascal/filesystem/delete")
    void delete(String uri, boolean recursive) throws IOException, URISyntaxException;

    @JsonRequest("rascal/filesystem/rename")
    void rename(String oldUri, String newUri, boolean overwrite) throws IOException, URISyntaxException;

    public static class FileChangeEvent {
        private final FileChangeType type;
        private final String uri;

        public FileChangeEvent(FileChangeType type, String uri) {
            this.type = type;
            this.uri = uri;
        }

        public FileChangeType getType() {
            return type;
        }

        public String getUri() {
            return uri;
        }
    }

    public static enum FileChangeType {
        Changed(1),
        Created(2),
        Deleted(3);

        private final int value;

        private FileChangeType(int val) {
            assert val == 1 || val == 2 || val == 3;
            this.value = val;
        }

        public int getValue() {
            return value;
        }
    };

    public static class FileStat {
        FileType type;
        long ctime;
        long mtime;
        long size;

        public FileStat(FileType type, long ctime, long mtime, long size) {
            this.type = type;
            this.ctime = ctime;
            this.mtime = mtime;
            this.size = size;
        }
    }

    public static enum FileType {
        Unknown(0),
        File(1),
        Directory(2),
        SymbolicLink(64);

        private final int value;

        private FileType(int val) {
            assert val == 0 || val == 1 || val == 2 || val == 64;
            this.value = val;
        }

        public int getValue() {
            return value;
        }
    };

    public static class FileWithType {
        private final String name;
        private final FileType type;

        public FileWithType(String name, FileType type) {
            this.name = name;
            this.type = type;
        }

        public String getName() {
            return name;
        }

        public FileType getType() {
            return type;
        }
    }
}
