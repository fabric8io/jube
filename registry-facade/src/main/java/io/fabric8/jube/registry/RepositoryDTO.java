package io.fabric8.jube.registry;

import java.util.Map;

public class RepositoryDTO {
    public String name;
    public String description;

    // helper functions for tests
    public String namespace() {
        return name.split("/")[0];
    }

    public String repository() {
        return name.split("/")[1];
    }

    @Override
    public String toString() {
        return "RepositoryDTO{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                '}';
    }
}
