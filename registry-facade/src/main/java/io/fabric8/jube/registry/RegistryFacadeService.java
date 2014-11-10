package io.fabric8.jube.registry;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import java.util.Map;

@Singleton
@Path("v1")
@Produces("application/json")
@Consumes("application/json")
public class RegistryFacadeService {

    @Inject
    private RegistryFacade facade;

    @GET
    @Path("search")
    @Produces("application/json")
    public RepositoriesDTO search(@QueryParam("q") String query) {
        return facade.search(query);
    }

    @GET
    @Path("repositories/{namespace}/{repository}/tags")
    @Produces("application/json")
    public Map<String,String> tags(@PathParam("namespace") @NotNull String namespace, @PathParam("repository") @NotNull String repository) {
        return facade.tags(namespace, repository);
    }

    @GET
    @Path("images/{imageID}/json")
    @Produces("application/json")
    public Object imageJson(@PathParam("imageID") @NotNull String imageID) {
        return facade.imageJson(imageID);
    }

}
