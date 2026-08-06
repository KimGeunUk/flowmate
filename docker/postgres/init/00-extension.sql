-- 시드 비밀번호를 SQL 안에서 BCrypt 해시로 만들기 위해 필요하다.
-- pgcrypto 의 crypt(pw, gen_salt('bf', 10)) 은 $2a$10$... 형식을 생성하고,
-- Spring Security 의 BCryptPasswordEncoder 가 그 형식을 그대로 검증한다.
-- 이 확장이 없으면 조직 시드 스크립트가 실패한다.
CREATE EXTENSION IF NOT EXISTS pgcrypto;
