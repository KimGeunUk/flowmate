CREATE TABLE department (
    dept_id        BIGSERIAL    PRIMARY KEY,
    parent_dept_id BIGINT       REFERENCES department(dept_id),
    dept_name      VARCHAR(100) NOT NULL,
    dept_code      VARCHAR(20)  NOT NULL UNIQUE,
    sort_order     INT          NOT NULL DEFAULT 0,
    use_yn         CHAR(1)      NOT NULL DEFAULT 'Y'
);
COMMENT ON TABLE  department            IS '부서 (자기참조 계층 구조)';
COMMENT ON COLUMN department.sort_order IS '같은 부모 아래 형제 부서의 표시 순서';

CREATE TABLE position (
    position_id    BIGSERIAL   PRIMARY KEY,
    position_name  VARCHAR(50) NOT NULL,
    position_level INT         NOT NULL
);
COMMENT ON TABLE  position                IS '직급';
COMMENT ON COLUMN position.position_level IS '1(사원)~6(이사). 결재선 정책과 부서장 판정이 참조한다';

CREATE TABLE employee (
    emp_id        BIGSERIAL    PRIMARY KEY,
    emp_no        VARCHAR(20)  NOT NULL UNIQUE,
    emp_name      VARCHAR(50)  NOT NULL,
    dept_id       BIGINT       NOT NULL REFERENCES department(dept_id),
    position_id   BIGINT       NOT NULL REFERENCES position(position_id),
    email         VARCHAR(100),
    hire_date     DATE         NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER',
    use_yn        CHAR(1)      NOT NULL DEFAULT 'Y'
);
COMMENT ON TABLE  employee               IS '사원 (로그인 주체)';
COMMENT ON COLUMN employee.emp_no        IS '사원번호. 로그인 아이디로 사용한다';
COMMENT ON COLUMN employee.password_hash IS 'BCrypt';
COMMENT ON COLUMN employee.role          IS 'USER / MANAGER / ADMIN';

CREATE INDEX idx_employee_dept ON employee(dept_id);
CREATE INDEX idx_employee_name ON employee(emp_name);
