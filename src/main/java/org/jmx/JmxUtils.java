package org.jmx;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.management.remote.JMXServiceURL;

import org.jmx.config.JvmInstanceConfiguration;
import org.jmx.connection.AttachApiConnector;
import org.jmx.connection.RmiConnector;
import org.jmx.connection.VirtualMachineConnector;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Range;
import com.sun.tools.attach.AgentInitializationException;
import com.sun.tools.attach.AgentLoadException;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

public final class JmxUtils {
    public static final String CONNECTOR_ADDRESS = "com.sun.management.jmxremote.localConnectorAddress";

    private JmxUtils() {
        throw new AssertionError( "Cannot instantiate " + getClass() );
    }

    public static void loadJMXAgent( VirtualMachine vm ) throws IOException, AgentLoadException, AgentInitializationException {
        String agent = vm.getSystemProperties().getProperty( "java.home" ) + File.separator + "lib" + File.separator + "management-agent.jar";
        vm.loadAgent( agent );
    }

    public static JMXServiceURL determineServiceUrl(VirtualMachine vm ) {
        try {
            String connectorAddress = vm.getAgentProperties().getProperty( JmxUtils.CONNECTOR_ADDRESS );
            //If jmx agent is not running in VM, load it
            if( connectorAddress == null ) {
                JmxUtils.loadJMXAgent( vm );

                // agent is started, get the connector address
                connectorAddress = vm.getAgentProperties().getProperty( JmxUtils.CONNECTOR_ADDRESS );
            }

            return new JMXServiceURL( connectorAddress );
        } catch( Exception e ) {
            throw Throwables.propagate( e );
        }
    }

    public static List<VirtualMachineConnector> enumerateJvms( List<JvmInstanceConfiguration> jvmInstanceConfigurations ) {
        List<VirtualMachineConnector> connectors = new ArrayList<>();
        enumerateViaAttachApi( jvmInstanceConfigurations, connectors );
        enumerateViaRmi( jvmInstanceConfigurations, connectors );
        return ImmutableList.copyOf( connectors );
    }

    private static void enumerateViaRmi( List<JvmInstanceConfiguration> jvmInstanceConfigurations, List<VirtualMachineConnector> connectors ) {
        for( JvmInstanceConfiguration jvmInstanceConfiguration : jvmInstanceConfigurations ) {
            Range<Integer> jmxPortRange = jvmInstanceConfiguration.getJmxPortRange();

            if( jmxPortRange == null ) {
                continue;
            }

            for( int jmxPort = jmxPortRange.lowerEndpoint(); jmxPort <= jmxPortRange.upperEndpoint(); jmxPort++ ) {
                connectors.add( new RmiConnector( jmxPort, jvmInstanceConfiguration ));
            }
        }
    }

    private static void enumerateViaAttachApi( List<JvmInstanceConfiguration> jvmInstanceConfigurations, List<VirtualMachineConnector> connectors ) {
        List<VirtualMachineDescriptor> descriptors = VirtualMachine.list();
        for( VirtualMachineDescriptor descriptor : descriptors ) {
            connectors.add( new AttachApiConnector( descriptor, jvmInstanceConfigurations ) );
        }
    }
}
