package com.citynavigator.resource;

import com.citynavigator.dto.AccountView;
import com.citynavigator.dto.LoginRequest;
import com.citynavigator.dto.RegisterRequest;
import com.citynavigator.dto.SavedPlaces;
import com.citynavigator.dto.SessionView;
import com.citynavigator.service.AccountService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * {@code /api/account}: opening an account, signing in, and the places the account keeps. Requests about an account
 * carry {@code Authorization: Bearer <token>} from the sign-in; they need no administrator token.
 */
@Path("account")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AccountResource {

    private AccountService accounts;

    protected AccountResource() {
        // for the CDI proxy
    }

    @Inject
    public AccountResource(AccountService accounts) {
        this.accounts = accounts;
    }

    @POST
    @Path("register")
    public Response register(@NotNull(message = "istek gövdesi gerekli") @Valid RegisterRequest request) {
        return Response.status(Response.Status.CREATED).entity(accounts.register(request)).build();
    }

    @POST
    @Path("login")
    public SessionView login(@NotNull(message = "istek gövdesi gerekli") @Valid LoginRequest request) {
        return accounts.login(request);
    }

    @GET
    public AccountView account(@HeaderParam(HttpHeaders.AUTHORIZATION) String authorization) {
        return accounts.account(accounts.accountOf(authorization));
    }

    @GET
    @Path("places")
    public SavedPlaces places(@HeaderParam(HttpHeaders.AUTHORIZATION) String authorization) {
        return accounts.places(accounts.accountOf(authorization));
    }

    @PUT
    @Path("places")
    public SavedPlaces replacePlaces(@HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
                                     @NotNull(message = "istek gövdesi gerekli") @Valid SavedPlaces places) {
        return accounts.replacePlaces(accounts.accountOf(authorization), places);
    }

    @DELETE
    public Response delete(@HeaderParam(HttpHeaders.AUTHORIZATION) String authorization) {
        accounts.delete(accounts.accountOf(authorization));
        return Response.noContent().build();
    }
}
