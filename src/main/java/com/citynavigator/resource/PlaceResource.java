package com.citynavigator.resource;

import com.citynavigator.dto.AlongPlaceView;
import com.citynavigator.dto.PlaceRequest;
import com.citynavigator.dto.PlaceView;
import com.citynavigator.dto.StreetView;
import com.citynavigator.routing.GeoPoint;
import com.citynavigator.routing.RoadsidePlace;
import com.citynavigator.routing.TravelMode;
import com.citynavigator.service.AlongRouteService;
import com.citynavigator.service.PlaceService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.util.List;
import java.util.Locale;

/**
 * {@code /api/places}: search by name, the street at a point, places along a route, and places added by an
 * administrator.
 */
@Path("places")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PlaceResource {

    private PlaceService places;
    private AlongRouteService along;

    protected PlaceResource() {
        // for the CDI proxy
    }

    @Inject
    public PlaceResource(PlaceService places, AlongRouteService along) {
        this.places = places;
        this.along = along;
    }

    /**
     * @param lat with {@code lon}: where the user is looking, so nearby results come first (optional)
     */
    @GET
    public List<PlaceView> search(
            @QueryParam("q") @NotNull(message = "gerekli") @Size(max = 100, message = "en fazla 100 karakter") String q,
            @QueryParam("limit") @DefaultValue("8") @Min(value = 1, message = "en az 1")
            @Max(value = 20, message = "en fazla 20") int limit,
            @QueryParam("lat") @DecimalMin(value = "-90", message = "geçersiz enlem")
            @DecimalMax(value = "90", message = "geçersiz enlem") Double lat,
            @QueryParam("lon") @DecimalMin(value = "-180", message = "geçersiz boylam")
            @DecimalMax(value = "180", message = "geçersiz boylam") Double lon) {
        return places.search(q, lat == null || lon == null ? null : new GeoPoint(lat, lon), limit);
    }

    @GET
    @Path("street")
    public StreetView streetAt(
            @QueryParam("lat") @NotNull(message = "gerekli") @DecimalMin(value = "-90", message = "geçersiz enlem")
            @DecimalMax(value = "90", message = "geçersiz enlem") Double lat,
            @QueryParam("lon") @NotNull(message = "gerekli") @DecimalMin(value = "-180", message = "geçersiz boylam")
            @DecimalMax(value = "180", message = "geçersiz boylam") Double lon) {
        return places.streetAt(new GeoPoint(lat, lon));
    }

    /**
     * {@code /api/places/along?need=fuel&mode=car&line=...}: fuel or charging stations, pharmacies, banks and ATMs or
     * markets along a planned route, which the application sends as an encoded polyline. A GET, like planning the
     * route itself: it changes nothing.
     */
    @GET
    @Path("along")
    public List<AlongPlaceView> along(
            @QueryParam("need") @NotNull(message = "gerekli")
            @Pattern(regexp = "fuel|charging|pharmacy|money|market",
                    message = "fuel, charging, pharmacy, money ya da market olmalı") String need,
            @QueryParam("mode") @DefaultValue("car")
            @Pattern(regexp = "car|bicycle|foot", message = "car, bicycle ya da foot olmalı") String mode,
            @QueryParam("line") @NotNull(message = "gerekli") @Size(max = 6000, message = "en fazla 6000 karakter")
            @Pattern(regexp = "[?-~]+", message = "kodlanmış bir çizgi olmalı") String line) {
        return along.along(RoadsidePlace.Kind.valueOf(need.toUpperCase(Locale.ROOT)),
                TravelMode.valueOf(mode.toUpperCase(Locale.ROOT)), line);
    }

    @GET
    @Path("{id: \\d+}")
    public PlaceView get(@PathParam("id") long id) {
        return places.get(id);
    }

    @POST
    public Response add(@NotNull(message = "istek gövdesi gerekli") @Valid PlaceRequest request,
                        @Context UriInfo uriInfo) {
        PlaceView created = places.add(request);
        return Response.created(uriInfo.getAbsolutePathBuilder().path(String.valueOf(created.id())).build())
                .entity(created)
                .build();
    }

    @DELETE
    @Path("{id: \\d+}")
    public Response delete(@PathParam("id") long id) {
        places.delete(id);
        return Response.noContent().build();
    }
}
