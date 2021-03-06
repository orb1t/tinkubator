/*
 * Copyright (c) 2009. The LoPSideD implementation of the Linked Process
 * protocol is an open-source project founded at the Center for Nonlinear Studies
 * at the Los Alamos National Laboratory in Los Alamos, New Mexico. Please visit
 * http://linkedprocess.org and LICENSE.txt for more information.
 */

package org.linkedprocess.villein.proxies;

import org.linkedprocess.LopError;
import org.linkedprocess.LinkedProcess;
import org.linkedprocess.farm.os.VmBindings;
import org.linkedprocess.farm.os.errors.InvalidValueException;
import org.linkedprocess.villein.Dispatcher;
import org.linkedprocess.villein.Handler;
import org.linkedprocess.villein.Villein;

import java.util.Set;

/**
 * A VmProxy is a proxy to a virtual machine. A virtual machine is an XMPP client that has a fully-qualified JID.
 * The bare JID component of the virtual machine is the virtual machine's countryside. A virtual machine is spawned from
 * a farm. Nearly all the computation that happens in an LoP cloud is through virtual machines.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 * @version LoPSideD 0.1
 */
public class VmProxy {

    /**
     * The password of this virtual machine.
     */
    protected FarmProxy farmProxy;
    protected String vmPassword;
    protected String vmId;
    protected Dispatcher dispatcher;
    /**
     * The species of this virtual machine.
     */
    protected String vmSpecies;
    /**
     * The current bindings of the virtual machine proxy. Note that the virtual machine proxy bindinds <i>are not</i>
     * in direct correspondence with the bindings of the virtual machine that this proxy represents.
     * The bindings of the virtual machine proxy need not be up to date nor be complete. These bindings are only updated
     * when bindings are retrieved using getBindings() and can not be guarenteed to be valid/up-to-date upon receipt.
     * It is up to the developer of the migrated code to understand how their bindings are being manipulated at the remote
     * virtual machine and to design around these constriants appropriately.
     */
    private VmBindings vmBindings = new VmBindings();

    public VmProxy(final FarmProxy farmProxy, final String vmId, final Dispatcher dispatcher) {
        this.farmProxy = farmProxy;
        this.vmId = vmId;
        this.dispatcher = dispatcher;
    }

    /**
     * Submit a job to the virtual machine for execution.
     *
     * @param jobProxy       the job to submit (requires at least an expression)
     * @param successHandler the handler called when a sucessful result has occurred
     * @param errorHandler   the handler called when an error result has occurred
     */
    public void submitJob(final JobProxy jobProxy, final Handler<JobProxy> successHandler, final Handler<JobProxy> errorHandler) {
        dispatcher.getSubmitJobCommand().send(this, jobProxy, successHandler, errorHandler);
    }

    /**
     * Ping a job that is being executed by the virtual machine to determine its status.
     *
     * @param jobProxy       the job to ping (requires at least the job id)
     * @param successHandler the handler called when a sucessful result has occurred
     * @param errorHandler   the handler called when an error result has occurred
     */
    public void pingJob(final JobProxy jobProxy, final Handler<LinkedProcess.JobStatus> successHandler, final Handler<LopError> errorHandler) {
        dispatcher.getPingJobCommand().send(this, jobProxy, successHandler, errorHandler);
    }

    /**
     * Abort a job that is being executed by the virtual machine.
     *
     * @param jobProxy       the job to abort (requires at least the job id)
     * @param successHandler the handler called when a sucessful result has occurred
     * @param errorHandler   the handler called when an error result has occurred
     */
    public void abortJob(final JobProxy jobProxy, final Handler<String> successHandler, final Handler<LopError> errorHandler) {
        dispatcher.getAbortJobCommand().send(this, jobProxy, successHandler, errorHandler);
    }

    /**
     * Get the binding values at the virtual machine.
     *
     * @param bindingNames   the name of the bindings to get
     * @param successHandler the handler called when a sucessful result has occurred
     * @param errorHandler   the handler called when an error result has occurred
     */
    public void getBindings(final Set<String> bindingNames, final Handler<VmBindings> successHandler, final Handler<LopError> errorHandler) {
        dispatcher.getGetBindingsCommand().send(this, bindingNames, successHandler, errorHandler);
    }

    /**
     * Set values to bindings at the virtual machine.
     *
     * @param vmBindings     the bindings to set
     * @param successHandler the handler called when a sucessful result has occurred
     * @param errorHandler   the handler called when an error result has occurred
     */
    public void setBindings(final VmBindings vmBindings, final Handler<VmBindings> successHandler, final Handler<LopError> errorHandler) {
        dispatcher.getSetBindingsCommand().send(this, vmBindings, successHandler, errorHandler);
    }

    /**
     * Terminate the virtual machine.
     *
     * @param successHandler the handler called when a sucessful result has occurred
     * @param errorHandler   the handler called when an error result has occurred
     */
    public void terminateVm(final Handler<Object> successHandler, final Handler<LopError> errorHandler) {
        dispatcher.getTerminateVmCommand().send(this, successHandler, errorHandler);
    }

    /**
     * Get the farm proxy that is the parent of this virtual machine proxy.
     *
     * @return the farm proxy parent of this virtual machine
     */
    public FarmProxy getFarmProxy() {
        return this.farmProxy;
    }

    /**
     * Set the virtual machine identifier (vm_id) of this virtual machine.
     *
     * @param vmId the virtual machine identifier of this virtual machine
     */
    public void setVmId(final String vmId) {
        this.vmId = vmId;
    }

    /**
     * Get the virtual machine identifier (vm_id) of this virtual machine.
     *
     * @return the virtual machine identifier of this virtual machine
     */
    public String getVmId() {
        return this.vmId;
    }

    /**
     * Set the species of the virtual machine. This is set when the virtual machine is spawned and in general, should not be changed.
     *
     * @param vmSpecies the species to set for the virtual machine
     */
    public void setVmSpecies(final String vmSpecies) {
        this.vmSpecies = vmSpecies;
    }

    /**
     * Get the species of the virtual machine.
     *
     * @return the species of the virtual machine
     */
    public String getVmSpecies() {
        return this.vmSpecies;
    }

    /**
     * Add the provided bindings to the bindings maintained at this virtual machine proxy.
     * This method is called when a get bindings is successfully returned
     *
     * @param bindings the bindings to add to the local virtual machine proxy bindings
     */
    public void addVmBindings(VmBindings bindings) {
        try {
            for (String bindingName : bindings.keySet()) {
                this.vmBindings.putTyped(bindingName, bindings.getTyped(bindingName));
            }
        } catch (InvalidValueException e) {
            Villein.LOGGER.warning(e.getMessage());
        }
    }

    /**
     * Remove the bindings from the local virtual machine proxy.
     * Note this does not remove the bindings from the remote virtual machine that this proxy represents.
     *
     * @param bindings the bindings to remove
     */
    public void removeVmBindings(VmBindings bindings) {
        for (String bindingName : bindings.keySet()) {
            this.vmBindings.remove(bindingName);
        }
    }

    /**
     * Get the bindings that are local to virtual machine proxy.
     * Note that the virtual machine proxy bindinds <i>are not</i>
     * in direct correspondence with the bindings of the virtual machine that this proxy represents.
     * The bindings of the virtual machine proxy need not be up to date nor be complete. These bindings are only updated
     * when bindings are retrieved using getBindings() and can not be guarenteed to be valid/up-to-date upon receipt.
     * It is up to the developer of the migrated code to understand how their bindings are being manipulated at the remote
     * virtual machine and to design around these constriants appropriately.
     *
     * @return the current bindings at the virtual machine proxy
     */
    public VmBindings getVmBindings() {
        return this.vmBindings;
    }


    public boolean equals(Object vmProxy) {
        return vmProxy instanceof VmProxy && ((VmProxy) vmProxy).getVmId().equals(this.vmId) && ((VmProxy)vmProxy).getFarmProxy().equals(this.farmProxy);
    }

    public int hashCode() {
        return (this.vmId + this.farmProxy.getJid()).hashCode();
    }
}

