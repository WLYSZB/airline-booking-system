package com.example.airline.central;

import com.example.airline.model.SearchCriteria;
import com.example.airline.model.SearchResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @PostMapping("/search")
    public SearchResult search(@RequestBody SearchCriteria criteria) {
        return searchService.search(criteria);
    }
}
