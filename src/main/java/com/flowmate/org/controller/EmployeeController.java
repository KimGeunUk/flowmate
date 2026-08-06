package com.flowmate.org.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.flowmate.org.domain.EmployeeSearchCond;
import com.flowmate.org.service.DepartmentService;
import com.flowmate.org.service.EmployeeService;

@Controller
@RequestMapping("/org")
public class EmployeeController {

    private final EmployeeService employeeService;
    private final DepartmentService departmentService;

    public EmployeeController(EmployeeService employeeService, DepartmentService departmentService) {
        this.employeeService = employeeService;
        this.departmentService = departmentService;
    }

    /**
     * cond 는 요청 파라미터가 바인딩된 뒤 화면으로 되돌려져 검색 폼의 값을 유지한다.
     * Service 가 페이지를 보정하면 그 값이 hidden page 에도 반영된다.
     * 모델 이름을 "paging" 으로 쓰는 것은 common/pagination.jsp 의 규약이다.
     */
    @GetMapping("/employees")
    public String list(EmployeeSearchCond cond, Model model) {
        model.addAttribute("paging", employeeService.search(cond));
        model.addAttribute("cond", cond);
        model.addAttribute("deptOptions", departmentService.findDeptTree());
        return "org/employee-list";
    }
}
