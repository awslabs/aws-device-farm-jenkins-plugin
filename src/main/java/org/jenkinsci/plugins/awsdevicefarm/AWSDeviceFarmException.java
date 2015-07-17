package org.jenkinsci.plugins.awsdevicefarm;

/**
 * Exception class for the the Device Farm Jenkins plugin.
 */
public class AWSDeviceFarmException extends Exception {
    /**
     * Low-IQ wrapper for generic Exceptions.
     * @param message The message to add to the exception.
     */
    public AWSDeviceFarmException(String message) {
        super(message);
    }
}
