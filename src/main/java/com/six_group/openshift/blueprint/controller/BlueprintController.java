package com.six_group.openshift.blueprint.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BlueprintController {

  @RequestMapping(value = "hello", method = RequestMethod.GET)
  public String hello() {
    return "Hello World";
  }

}
