package io.fabric8.jube.registry;

import java.util.List;

public class RepositoriesDTO {
    public int num_results;
    public String query;
    public List<RepositoryDTO> results;

    @Override
    public String toString() {
        return "RepositoriesDTO{" +
                "num_results=" + num_results +
                ", query='" + query + '\'' +
                ", results=" + results +
                '}';
    }


}
