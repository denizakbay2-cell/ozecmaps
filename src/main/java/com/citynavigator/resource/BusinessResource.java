package com.citynavigator.resource;

import com.citynavigator.dto.PlaceView;
import com.citynavigator.service.PlaceService;
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
 * {@code /api/businesses?minLat=&minLon=&maxLat=&maxLon=}: the businesses inside a street-level map view.
 */
@Path("businesses")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
public class BusinessResource {

    private PlaceService places;

    protected BusinessResource() {
        // for the CDI proxy
    }

    @Inject
    public BusinessResource(PlaceService places) {
        this.places = places;
    }

    @GET
    public List<PlaceView> within(
            @QueryParam("minLat") @NotNull(message = "gerekli") @DecimalMin(value = "-90", message = "geçersiz enlem")
            @DecimalMax(value = "90", message = "geçersiz enlem") Double minLat,
            @QueryParam("minLon") @NotNull(message = "gerekli") @DecimalMin(value = "-180", message = "geçersiz boylam")
            @DecimalMax(value = "180", message = "geçersiz boylam") Double minLon,
            @QueryParam("maxLat") @NotNull(message = "gerekli") @DecimalMin(value = "-90", message = "geçersiz enlem")
            @DecimalMax(value = "90", message = "geçersiz enlem") Double maxLat,
            @QueryParam("maxLon") @NotNull(message = "gerekli") @DecimalMin(value = "-180", message = "geçersiz boylam")
            @DecimalMax(value = "180", message = "geçersiz boylam") Double maxLon) {
        return places.businessesWithin(minLat, minLon, maxLat, maxLon);
    }
}
