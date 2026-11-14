package com.citynavigator.resource;

import com.citynavigator.dto.IncidentRequest;
import com.citynavigator.dto.IncidentView;
import com.citynavigator.service.IncidentService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.util.List;

/**
 * {@code /api/incidents}: accidents, roadworks, congestion and closures that routes take into account.
 */
@Path("incidents")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class IncidentResource {

    private IncidentService incidents;

    protected IncidentResource() {
        // for the CDI proxy
    }

    @Inject
    public IncidentResource(IncidentService incidents) {
        this.incidents = incidents;
    }

    @GET
    public List<IncidentView> active() {
        return incidents.active();
    }

    @GET
    @Path("{id: \\d+}")
    public IncidentView get(@PathParam("id") long id) {
        return incidents.get(id);
    }

    @POST
    public Response report(@NotNull(message = "istek gövdesi gerekli") @Valid IncidentRequest request,
                           @Context UriInfo uriInfo) {
        IncidentView created = incidents.report(request);
        return Response.created(uriInfo.getAbsolutePathBuilder().path(String.valueOf(created.id())).build())
                .entity(created)
                .build();
    }

    /**
     * Marks the road clear again. A POST on a sub-resource, because it is an action with a rule (only once),
     * not a replacement of the incident.
     */
    @POST
    @Path("{id: \\d+}/resolve")
    public IncidentView resolve(@PathParam("id") long id) {
        return incidents.resolve(id);
    }
}
