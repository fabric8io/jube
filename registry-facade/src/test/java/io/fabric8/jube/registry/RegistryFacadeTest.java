package io.fabric8.jube.registry;

import org.junit.AfterClass;
import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Map;
import java.util.Set;

public class RegistryFacadeTest {

    protected static RegistryFacade facade;

    @BeforeClass
    public static void before() throws Exception {
        facade = new RegistryFacade();
        facade.init();
    }

    @AfterClass
    public static void after() throws Exception {
        facade.destroy();
    }

    @Test
    public void testSearch() throws Exception {
        RepositoriesDTO repositories = facade.search("karaf");
        // should get back something!
        assertTrue(!repositories.results.isEmpty());
    }

    @Test
    public void testTags() throws Exception {
        RepositoriesDTO repositories = facade.search("karaf");
        assertTrue(!repositories.results.isEmpty());
        for (RepositoryDTO repository : repositories.results) {
            Map<String, String> tags = facade.tags(repository.namespace(), repository.repository());
            Set<String> keys = tags.keySet();
            assertTrue(!keys.isEmpty());
        }
    }

    @Test
    public void testImageJson() throws Exception {
        RepositoriesDTO repositories = facade.search("karaf");
        assertTrue(!repositories.results.isEmpty());
        for (RepositoryDTO repository : repositories.results) {
            Map<String, String> tags = facade.tags(repository.namespace(), repository.repository());
            Set<String> keys = tags.keySet();
            assertTrue(!keys.isEmpty());
            for (String key : keys) {
                Object json = facade.imageJson(tags.get(key));
                assertTrue(json != null);
            }
        }
    }
}
