package org.linkedprocess;

import static org.easymock.EasyMock.*;
import static org.easymock.EasyMock.expectLastCall;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.Roster;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smackx.ServiceDiscoveryManager;
import org.jivesoftware.smackx.packet.DataForm;
import org.junit.After;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.linkedprocess.xmpp.XMPPConnectionWrapper;
import org.linkedprocess.xmpp.XmppClient;
import org.linkedprocess.xmpp.farm.PresenceSubscriptionListener;
import org.linkedprocess.xmpp.farm.SpawnVm;
import org.linkedprocess.xmpp.farm.SpawnVmListener;
import org.linkedprocess.xmpp.farm.XmppFarm;
import org.linkedprocess.xmpp.vm.*;
import static org.powermock.api.easymock.PowerMock.createMock;
import static org.powermock.api.easymock.PowerMock.*;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

@RunWith(PowerMockRunner.class)
@PrepareForTest({XmppFarm.class, XmppClient.class,
        ServiceDiscoveryManager.class})
// ignore fishy stuff
@PowerMockIgnore({"javax.script", "org.apache.log4j"})
public class XMPPSpecificationTest {

    private static String username1 = "linked.process.4@gmail.com";
    private static String password1 = "linked45";

    private static String server = "talk1.l.google.com";
    private static int port = 5222;
    private String mockFarmId;
    private String mockClient = "mockClient";
    private String spawnPacketId = "123";
    private MockXMPPConnection mockFarmConn;
    private XmppFarm xmppFarm;
    // private SpawnVm spawn;
    private ServiceDiscoveryManager mdm;
    private MockXMPPConnection mockVM1Conn;
    private XMPPConnection mockXmppConnection;
    private Roster mockRoster;
    private ArrayList<Packet> sentPacketsVM1;

    @Test
    public void subscribingToAFarmsRosterShouldResultInThreePresencePacketsBack()
            throws Exception {

        // activate all mock objects
        replayAll();

        xmppFarm = new XmppFarm(server, port, username1, password1);
        ArrayList<Packet> sentPackets = mockFarmConn.sentPackets;
        assertEquals(1, sentPackets.size());
        // get rid of the startup stuff
        mockFarmConn.clearPackets();
        ArrayList<PacketListener> packetListeners = mockFarmConn.packetListeners;
        // second registered listener should be our SpawnListener
        PresenceSubscriptionListener subscriptionListener = (PresenceSubscriptionListener) packetListeners
                .get(1);
        Presence subscriptionPacket = new Presence(Presence.Type.subscribe);
        subscriptionPacket.setTo(mockFarmId);
        subscriptionPacket.setFrom(mockClient);
        subscriptionListener.processPacket(subscriptionPacket);
        assertEquals(3, sentPackets.size());
        // subscription acc
        Presence p0 = (Presence) sentPackets.get(0);
        assertEquals(Presence.Type.subscribed, p0.getType());
        // subscribe request to the client
        Presence p1 = (Presence) sentPackets.get(1);
        assertEquals(Presence.Type.subscribe, p1.getType());
        // Farm status
        Presence p2 = (Presence) sentPackets.get(2);
        assertEquals(Presence.Type.available, p2.getType());
        assertEquals(p2.getPriority(), LinkedProcess.HIGHEST_PRIORITY);
        assertEquals(p2.getStatus(), XmppFarm.STATUS_MESSAGE_ACTIVE);
        xmppFarm.shutDown();

    }

    @Test
    public void checkCorrectStartupAndShutdownPacketsAndListeners()
            throws Exception {

        // activate all mock objects
        replayAll();

        // start the farm
        xmppFarm = new XmppFarm(server, port, username1, password1);

        // two presence packets should have been sent upon connecting
        ArrayList<Packet> sentPackets = mockFarmConn.sentPackets;
        assertEquals(1, sentPackets.size());
        // Farm started
        assertEquals(Presence.Type.available, ((Presence) sentPackets.get(0))
                .getType());
        assertEquals(LinkedProcess.HIGHEST_PRIORITY, ((Presence) sentPackets
                .get(0)).getPriority());

        // now we should have 2 PacketListeners to the Farms XMPP connection
        ArrayList<PacketListener> packetListeners = mockFarmConn.packetListeners;
        assertEquals(2, packetListeners.size());
        assertTrue(packetListeners.get(0) instanceof SpawnVmListener);
        assertTrue(packetListeners.get(1) instanceof PresenceSubscriptionListener);

        xmppFarm.shutDown();
        assertEquals(2, sentPackets.size());

        assertEquals(Presence.Type.unavailable, ((Presence) sentPackets.get(1))
                .getType());
    }

    @Test
    public void sendingASpawnPacketShouldStartASeparateVM() throws Exception {
        expectNew(XMPPConnectionWrapper.class,
                isA(ConnectionConfiguration.class)).andReturn(mockVM1Conn);

        expectNew(XMPPConnectionWrapper.class,
                isA(ConnectionConfiguration.class)).andReturn(
                mockXMPPConnection("secondVM"));

        // activate all mock objects
        replayAll();

        // start the farm
        xmppFarm = new XmppFarm(server, port, username1, password1);
        ArrayList<Packet> sentPackets = mockFarmConn.sentPackets;
        mockFarmConn.clearPackets();
        SpawnVm spawn = createSpawnPacket();
        spawn.setType(IQ.Type.GET);
        // let's send a spawn packet to the SpwnVMListener!
        mockFarmConn.packetListeners.get(0).processPacket(spawn);
        // now, a new packet should have been sent back from the VM
        assertEquals(1, sentPackets.size());
        // sent packet should refer to the same pID
        SpawnVm result = (SpawnVm) sentPackets.get(0);
        assertEquals(result.getPacketID(), spawnPacketId);
        assertEquals(result.getType(), IQ.Type.RESULT);
        assertFalse("The returned VM ID should not be the Farms id, right?",
                mockFarmConn.getUser().equals(mockVM1Conn.getUser()));
        // now we should have 3 PacketListeners for the VM
        ArrayList<PacketListener> vm1packetListeners = mockVM1Conn.packetListeners;
        assertEquals(5, vm1packetListeners.size());
        assertTrue(vm1packetListeners.get(0) instanceof SubmitJobListener);
        assertTrue(vm1packetListeners.get(1) instanceof JobStatusListener);
        assertTrue(vm1packetListeners.get(2) instanceof AbortJobListener);
        assertTrue(vm1packetListeners.get(3) instanceof TerminateVmListener);
        assertTrue(vm1packetListeners.get(4) instanceof ManageBindingsListener);

        // try one more VM with the same spawn packet as the first!
        mockFarmConn.clearPackets();
        mockFarmConn.packetListeners.get(0).processPacket(spawn);
        assertEquals(1, sentPackets.size());
        result = (SpawnVm) sentPackets.get(0);
        assertEquals(result.getPacketID(), spawnPacketId);
        assertEquals(
                "We should not be able to spwn the same type of VM twice!",
                result.getType(), IQ.Type.RESULT);
        mockVM1Conn.clearPackets();
        mockFarmConn.clearPackets();
        xmppFarm.shutDown();
        sentPacketsVM1 = mockVM1Conn.sentPackets;
        System.out.println(sentPackets);
        assertEquals("We were expecting one UNAVAILABE", 1,
                sentPackets.size());
        assertEquals(Presence.Type.unavailable, ((Presence) sentPackets.get(0))
                .getType());
        //VM is not longer sending an unavailable ...
//        assertEquals(1, sentPacketsVM1.size());
//        assertEquals(Presence.Type.unavailable, ((Presence) sentPacketsVM1
//                .get(0)).getType());
    }

    @Test
    public void tryingToSpawnAVMWithWrongSpecShouldReturnAnError()
            throws Exception {
        expectNew(XMPPConnectionWrapper.class,
                isA(ConnectionConfiguration.class)).andReturn(
                mockXMPPConnection("mockVM"));

        // activate all mock objects
        replayAll();

        // start the farm
        xmppFarm = new XmppFarm(server, port, username1, password1);
        SpawnVm spawn = createSpawnPacket();
        // sent a non-existent specification
        spawn.setVmSpecies("dummy");
        mockFarmConn.clearPackets();
        mockFarmConn.packetListeners.get(0).processPacket(spawn);
        ArrayList<Packet> sentPackets = mockFarmConn.sentPackets;
        assertEquals(1, sentPackets.size());
        SpawnVm result = (SpawnVm) sentPackets.get(0);
        assertEquals(result.getPacketID(), spawnPacketId);
        assertEquals(
                "We should not be able to spawn the a VM with a non-existent Script type!",
                result.getType(), IQ.Type.ERROR);
        //assertEquals(LinkedProcess.LopErrorType.SPECIES_NOT_SUPPORTED, result.getErrorType());

        xmppFarm.shutDown();
    }


    @Test
    public void checkingStatusOnNonExistingJobShouldReturnError() throws Exception {
        expectNew(XMPPConnectionWrapper.class,
                isA(ConnectionConfiguration.class)).andReturn(mockVM1Conn);

        // activate all mock objects
        replayAll();

        // start the farm
        xmppFarm = new XmppFarm(server, port, username1, password1);
        SpawnVm spawn = createSpawnPacket();

        // let's send a spawn packet to the SpwnVMListener!
        mockFarmConn.packetListeners.get(0).processPacket(spawn);
        // get the password
        SpawnVm vmAcc = (SpawnVm) mockFarmConn.sentPackets
                .get(mockFarmConn.sentPackets.size() - 1);
        String vmPassword = vmAcc.getVmPassword();
        String vmJid = vmAcc.getVmJid();

        JobStatus status = new JobStatus();
        status.setVmPassword(vmPassword);
        status.setTo("dummy");
        status.setPacketID(spawnPacketId);

        //check without job id
        mockVM1Conn.clearPackets();
        mockVM1Conn.packetListeners.get(1).processPacket(status);
        waitForResponse(mockFarmConn.sentPackets, 1000);
        // now, a new packet should have been sent back from the VM
        assertEquals(1, mockVM1Conn.sentPackets.size());
        // sent packet should refer to the same pID
        JobStatus result = (JobStatus) mockVM1Conn.sentPackets.get(0);
        assertEquals(spawnPacketId, result.getPacketID());
        assertEquals(IQ.Type.ERROR, result.getType());
        //assertEquals(LinkedProcess.LopErrorType.MALFORMED_PACKET, result.getErrorType());

        //non-existent job
        status.setJobId("test");
        mockVM1Conn.clearPackets();
        mockVM1Conn.packetListeners.get(1).processPacket(status);
        waitForResponse(mockFarmConn.sentPackets, 1000);
        // now, a new packet should have been sent back from the VM
        assertEquals(1, mockVM1Conn.sentPackets.size());
        // sent packet should refer to the same pID
        result = (JobStatus) mockVM1Conn.sentPackets.get(0);
        assertEquals(spawnPacketId, result.getPacketID());
        assertEquals(IQ.Type.ERROR, result.getType());
        //assertEquals(LopErrorType.JOB_NOT_FOUND, result.getErrorType());

    }

    @Test
    public void sendingAnEvalPacketWithoutVMPasswordShouldReturnErrorAndWithPasswordAResult()
            throws Exception {
        expectNew(XMPPConnectionWrapper.class,
                isA(ConnectionConfiguration.class)).andReturn(mockVM1Conn);

        // activate all mock objects
        replayAll();

        // start the farm
        xmppFarm = new XmppFarm(server, port, username1, password1);
        SpawnVm spawn = createSpawnPacket();

        // let's send a spawn packet to the SpwnVMListener!
        mockFarmConn.packetListeners.get(0).processPacket(spawn);
        // get the password
        SpawnVm vmAcc = (SpawnVm) mockFarmConn.sentPackets
                .get(mockFarmConn.sentPackets.size() - 1);
        String vmPassword = vmAcc.getVmPassword();
        String vmJid = vmAcc.getVmJid();

        // send the eval packet, password missing
        SubmitJob eval = new SubmitJob();
        eval.setPacketID(spawnPacketId);
        eval.setExpression("20 + 52;");
        eval.setTo(vmJid);
        eval.setFrom(mockClient);
        mockVM1Conn.clearPackets();
        // ArrayList<Packet> vmsentPackets = mockVM1Conn.sentPackets;
        mockVM1Conn.packetListeners.get(0).processPacket(eval);
        waitForResponse(mockFarmConn.sentPackets, 1000);
        // now, a new packet should have been sent back from the VM
        assertEquals(1, mockVM1Conn.sentPackets.size());
        // sent packet should refer to the same pID
        SubmitJob result = (SubmitJob) mockVM1Conn.sentPackets.get(0);
        assertEquals(result.getPacketID(), spawnPacketId);
        assertEquals(IQ.Type.ERROR, result.getType());
        //assertEquals(LinkedProcess.LopErrorType.MALFORMED_PACKET, result.getErrorType());

        // now, try with a wrong password
        eval.setVmPassword("wrong");
        mockVM1Conn.clearPackets();
        assertEquals(0, mockVM1Conn.sentPackets.size());
        mockVM1Conn.packetListeners.get(0).processPacket(eval);
        waitForResponse(mockVM1Conn.sentPackets, 1500);
        // now, a new packet should have been sent back from the VM
        assertEquals(1, mockVM1Conn.sentPackets.size());
        // sent packet should refer to the same pID
        result = (SubmitJob) mockVM1Conn.sentPackets.get(0);
        assertEquals(result.getPacketID(), spawnPacketId);
        assertEquals(IQ.Type.ERROR, result.getType());
        //assertEquals(LopErrorType.WRONG_VM_PASSWORD, result.getErrorType());

        // now, try with a valid password
        eval.setVmPassword(vmPassword);
        mockVM1Conn.clearPackets();
        assertEquals(0, mockVM1Conn.sentPackets.size());
        mockVM1Conn.packetListeners.get(0).processPacket(eval);
        waitForResponse(mockVM1Conn.sentPackets, 1500);
        // now, a new packet should have been sent back from the VM
        assertEquals(1, mockVM1Conn.sentPackets.size());
        // sent packet should refer to the same pID
        result = (SubmitJob) mockVM1Conn.sentPackets.get(0);
        assertEquals(result.getPacketID(), spawnPacketId);
        assertEquals(IQ.Type.RESULT, result.getType());
        assertEquals("72.0", result.getExpression());

        // now, try with a wrong evaluation
        eval.setExpression("buh+2sdf;==");
        mockVM1Conn.clearPackets();
        assertEquals(0, mockVM1Conn.sentPackets.size());
        mockVM1Conn.packetListeners.get(0).processPacket(eval);
        waitForResponse(mockVM1Conn.sentPackets, 1500);
        // now, a new packet should have been sent back from the VM
        assertEquals(1, mockVM1Conn.sentPackets.size());
        // sent packet should refer to the same pID
        result = (SubmitJob) mockVM1Conn.sentPackets.get(0);
        assertEquals(result.getPacketID(), spawnPacketId);
        assertEquals(IQ.Type.ERROR, result.getType());
        //assertEquals(LopErrorType.EVALUATION_ERROR, result.getErrorType());

        // shut down the VM
        mockVM1Conn.clearPackets();
        TerminateVm terminate = new TerminateVm();
        terminate.setVmPassword(vmPassword);
        terminate.setTo(vmJid);
        mockVM1Conn.packetListeners.get(3).processPacket(terminate);

        ArrayList<Packet> sentPackets = mockVM1Conn.sentPackets;
        assertEquals("we should get one result back", 1, sentPackets
                .size());
        assertEquals(IQ.Type.RESULT, ((IQ) sentPackets.get(0)).getType());
        // check right shutdown messages
        xmppFarm.shutDown();
    }

    private void waitForResponse(Collection watching, int timeout) {
        int currentSize = watching.size();
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() < startTime + timeout) {
            if (watching.size() > currentSize) {
                return;
            }
            try {
                Thread.sleep(50);
                // System.out.println(watching.size() + " > " + currentSize);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

        }

    }

    @Test
    public void sendingATerminatePacketShouldCloseTheVM() throws Exception {
        expectNew(XMPPConnectionWrapper.class,
                isA(ConnectionConfiguration.class)).andReturn(mockVM1Conn);

        // activate all mock objects
        replayAll();

        // start the farm
        xmppFarm = new XmppFarm(server, port, username1, password1);
        SpawnVm spawn = createSpawnPacket();

        // let's send a spawn packet to the SpwnVMListener!
        mockFarmConn.packetListeners.get(0).processPacket(spawn);
        // get the password
        SpawnVm vmAcc = (SpawnVm) mockFarmConn.sentPackets
                .get(mockFarmConn.sentPackets.size() - 1);
        String vmPassword = vmAcc.getVmPassword();
        String vmJid = vmAcc.getVmJid();

        TerminateVm terminate = new TerminateVm();
        terminate.setVmPassword(vmPassword);
        terminate.setTo(vmJid);
        mockVM1Conn.clearPackets();
        mockVM1Conn.packetListeners.get(3).processPacket(terminate);
        ArrayList<Packet> sentPackets = mockVM1Conn.sentPackets;
        System.out.println(sentPackets);
        assertEquals("we should get back one IQ TERMIANTE RESULT "
                + "and one UNAVAILABLE presence packet back.", 2, sentPackets
                .size());
        // first one - IQ
        TerminateVm result = (TerminateVm) sentPackets.get(0);
        assertEquals(IQ.Type.RESULT, result.getType());
        assertEquals(terminate.getPacketID(), result.getPacketID());
        // second one should be the unavailable presence
        assertEquals(Presence.Type.unavailable, ((Presence) sentPackets.get(1))
                .getType());

    }

    private SpawnVm createSpawnPacket() {
        SpawnVm spawn = new SpawnVm();
        spawn.setVmSpecies("javascript");
        spawn.setPacketID(spawnPacketId);
        spawn.setFrom(mockClient);
        spawn.setType(IQ.Type.GET);
        return spawn;
    }

    @After
    public void shutdown() {
        // shut down
        // xmppFarm.shutDown();
        verifyAll();

    }

    @Before
    public void setup() throws Exception {
        mdm = createMock(ServiceDiscoveryManager.class);
        mockXmppConnection = createMock(XMPPConnection.class);
        mockFarmId = "LopServer/LoPFarm";
        mockFarmConn = mockXMPPConnection("mockfarm");
        mockVM1Conn = mockXMPPConnection("VM1");
        mockStatic(ServiceDiscoveryManager.class);
        expect(ServiceDiscoveryManager.getInstanceFor(mockXmppConnection))
                .andReturn(mdm);
        expectLastCall().anyTimes();
        Iterator<String> features = new LinkedList<String>().iterator();
        expect(mdm.getFeatures()).andReturn(features).anyTimes();
        mdm.addFeature(isA(String.class));
        expectLastCall().anyTimes();
        mdm.setExtendedInfo(isA(DataForm.class));
        expectLastCall().anyTimes();
        ServiceDiscoveryManager.setIdentityName(isA(String.class));
        expectLastCall().anyTimes();
        ServiceDiscoveryManager.setIdentityType(LinkedProcess.DISCO_BOT);
        expectLastCall().anyTimes();
        // the magic of inserting the mock connections!
        // first call is cfor the LoPFarm
        expectNew(XMPPConnectionWrapper.class,
                isA(ConnectionConfiguration.class)).andReturn(mockFarmConn);
        // next time, return the VM1 connection

    }

    private MockXMPPConnection mockXMPPConnection(String id) throws Exception {
        MockXMPPConnection mocConnection = new MockXMPPConnection(
                new ConnectionConfiguration(server, port), id);
        mocConnection.setDelegate(mockXmppConnection);

        mockRoster = createMock(Roster.class);
        mockRoster.setSubscriptionMode(Roster.SubscriptionMode.manual);
        expectLastCall().anyTimes();
        mocConnection.setRoster(mockRoster);
        mockRoster.createEntry(mockClient, mockClient, null);
        expectLastCall().anyTimes();
        return mocConnection;

    }

}
