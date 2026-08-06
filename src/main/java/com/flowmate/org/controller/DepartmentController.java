package com.flowmate.org.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.flowmate.org.service.DepartmentService;

@Controller
@RequestMapping("/org")
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @GetMapping("/dept-tree")
    public String deptTree(Model model) {
        model.addAttribute("deptTree", departmentService.findDeptTree());
        return "org/dept-tree";
    }
}
