/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package io.seqware.webservice.generated.controller;

import io.seqware.webservice.generated.model.IusLink;
import java.util.List;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

/**
 * 
 * @author boconnor
 */
@Stateless
@Path("io.seqware.webservice.model.iuslink")
public class IusLinkFacadeREST extends AbstractFacade<IusLink> {
    @PersistenceContext(unitName = "io.seqware_seqware-admin-webservice_war_1.0-SNAPSHOTPU")
    private EntityManager em;

    public IusLinkFacadeREST() {
        super(IusLink.class);
    }

    @POST
    @Override
    @Consumes({ "application/xml", "application/json" })
    public void create(IusLink entity) {
        super.create(entity);
    }

    @PUT
    @Override
    @Consumes({ "application/xml", "application/json" })
    public void edit(IusLink entity) {
        super.edit(entity);
    }

    @DELETE
    @Path("{id}")
    public void remove(@PathParam("id") Integer id) {
        super.remove(super.find(id));
    }

    @GET
    @Path("{id}")
    @Produces({ "application/xml", "application/json" })
    public IusLink find(@PathParam("id") Integer id) {
        return super.find(id);
    }

    @GET
    @Override
    @Produces({ "application/xml", "application/json" })
    public List<IusLink> findAll() {
        return super.findAll();
    }

    @GET
    @Path("{from}/{to}")
    @Produces({ "application/xml", "application/json" })
    public List<IusLink> findRange(@PathParam("from") Integer from, @PathParam("to") Integer to) {
        return super.findRange(new int[] { from, to });
    }

    @GET
    @Path("count")
    @Produces("text/plain")
    public String countREST() {
        return String.valueOf(super.count());
    }

    @Override
    protected EntityManager getEntityManager() {
        return em;
    }

}
