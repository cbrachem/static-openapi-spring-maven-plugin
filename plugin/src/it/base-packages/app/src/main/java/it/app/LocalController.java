package it.app;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LocalController {

    @GetMapping("/local/{name}")
    public String find(@PathVariable String name) {
        return name;
    }
}
