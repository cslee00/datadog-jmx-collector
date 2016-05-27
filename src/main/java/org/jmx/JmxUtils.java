package org.jmx;

import java.io.File;
import java.io.IOException;

import com.sun.tools.attach.AgentInitializationException;
import com.sun.tools.attach.AgentLoadException;
import com.sun.tools.attach.VirtualMachine;

public final class JmxUtils {
    public static final String CONNECTOR_ADDRESS = "com.sun.management.jmxremote.localConnectorAddress";

    private JmxUtils() {
        throw new AssertionError( "Cannot instantiate " + getClass() );
    }

    public static void loadJMXAgent( VirtualMachine vm ) throws IOException, AgentLoadException, AgentInitializationException {
        String agent = vm.getSystemProperties().getProperty( "java.home" ) + File.separator + "lib" + File.separator + "management-agent.jar";
        vm.loadAgent( agent );
    }
}
