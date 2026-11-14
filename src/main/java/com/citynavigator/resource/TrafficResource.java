package com.citynavigator.resource;

import com.citynavigator.dto.TrafficRoadView;
import com.citynavigator.dto.TrafficStatus;
import com.citynavigator.service.TrafficService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

/**
 * {@code /api/traffic}: how busy Istanbul is now; {@code /api/traffic/roads?minLat=&minLon=&maxLat=&maxLon=}: the
 * roads of a map view coloured by traffic.
 */
@Path("traffic")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
public class TrafficResource {

    private TrafficService traffic;

    protected TrafficResource() {
        // for the CDI proxy
    }

    @Inject
    public TrafficResource(TrafficService traffic) {
        this.traffic = traffic;
    }

    @GET
    public TrafficStatus status() {
        return traffic.status();
    }

    @GET
    @Path("roads")
    public List<TrafficRoadView> roads(
            @QueryParam("minLat") @NotNull(message = "gerekli") @DecimalMin(value = "-90", message = "geçersiz enlem")
            @DecimalMax(value = "90", message = "geçersiz enlem") Double minLat,
            @QueryParam("minLon") @NotNull(message = "gerekli") @DecimalMin(value = "-180", message = "geçersiz boylam")
            @DecimalMax(value = "180", message = "geçersiz boylam") Double minLon,
            @QueryParam("maxLat") @NotNull(message = "gerekli") @DecimalMin(value = "-90", message = "geçersiz enlem")
            @DecimalMax(value = "90", message = "geçersiz enlem") Double maxLat,
            @QueryParam("maxLon") @NotNull(message = "gerekli") @DecimalMin(value = "-180", message = "geçersiz boylam")
            @DecimalMax(value = "180", message = "geçersiz boylam") Double maxLon) {
        return traffic.roadsWithin(minLat, minLon, maxLat, maxLon);
    }
}
