package org.jmx.connection;

public class UnableToAttachException extends Exception {
    public UnableToAttachException( String message ) {
        super( message );
    }
    public UnableToAttachException( String message, Throwable cause ) {
        super( cause );
    }
}
