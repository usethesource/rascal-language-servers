package org.rascalmpl.vscode.lsp.uri.jsonrpc.messages;

import java.util.Objects;
import org.eclipse.lsp4j.jsonrpc.validation.NonNull;
import org.rascalmpl.vscode.lsp.util.locations.Locations;
import io.usethesource.vallang.ISourceLocation;

public class ISourceLocationChanged {
    @NonNull
    private String watchId;
    @NonNull
    private String location;
    @NonNull
    private ISourceLocationChangeType changeType;
    @NonNull
    private ISourceLocationType type;


    public ISourceLocationChanged() {
    }

    public ISourceLocationChanged(@NonNull String watchId, @NonNull String location, @NonNull ISourceLocationChangeType changeType, @NonNull ISourceLocationType type) {
        this.watchId = watchId;
        this.location = location;
        this.changeType = changeType;
        this.type = type;
    }

    public ISourceLocationChangeType getChangeType() {
        return changeType;
    }
    public String getLocation() {
        return location;
    }
    public ISourceLocation getSourceLocation() {
        return Locations.toLoc(location);
    }
    public ISourceLocationType getType() {
        return type;
    }

    public String getWatchId() {
        return watchId;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ISourceLocationChanged) {
            var other = (ISourceLocationChanged)obj;
            return Objects.equals(watchId, other.watchId)
                && Objects.equals(location, other.location)
                && Objects.equals(changeType, other.changeType)
                && Objects.equals(type, other.type)
                ;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(watchId, location, changeType, type);
    }



}
