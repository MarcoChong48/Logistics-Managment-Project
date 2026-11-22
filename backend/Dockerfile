# Build using Java 21
FROM eclipse-temurin:21-jdk AS source

WORKDIR /app

COPY .mvn .mvn
COPY mvnw pom.xml ./

RUN sed -i 's/\r$//' mvnw && chmod +x mvnw

COPY src ./src

# Run the pure rule checks without the Compose database.
FROM source AS rule-check

RUN ./mvnw -B -ntp "-Dtest=*RuleCheckTest" test

# TEMPORARY TEST CODE - remove before integration
# Stage 4 runs checked classes in stage order against the disposable Compose database.
FROM source AS flow-check

CMD ["flock", "/flow-lock/lms_flow_check.lock", "sh", "-c", "\
    shared_rule_status=0; shared_flow_status=0; \
    ./mvnw -B -ntp '-Dtest=*RuleCheckTest' test || shared_rule_status=1; \
    run_flow() { ./mvnw -B -ntp \"-Dtest=$1\" test || shared_flow_status=1; }; \
    save_early_report() { \
      suite_report=target/surefire-reports/TEST-com.lms.backend.$1; \
      cp \"$suite_report.xml\" \"$suite_report-early.xml\" || shared_flow_status=1; \
    }; \
    ./mvnw -B -ntp -Dlms.flow.initialize=true \
      '-Dtest=SharedSetupFlowCheckTest#checkS0T01+checkS0T02+checkS0T03+checkS0T04+checkS0T09' test \
      || shared_flow_status=1; \
    save_early_report SharedSetupFlowCheckTest; \
    run_flow 'SharedLoginFlowCheckTest#checkS1T01+checkS1T02+checkS1T03'; \
    save_early_report SharedLoginFlowCheckTest; \
    run_flow 'SharedSetupFlowCheckTest#checkS0T05+checkS0T06+checkS0T07+checkS0T08+checkS0T10+checkS0T11'; \
    run_flow 'SharedLoginFlowCheckTest#checkS1T04+checkS1T05+checkS1T06+checkS1T07+checkS1T08+checkS1T09+checkS1T10+checkS1T11+checkS1T12+checkS1T13+checkS1T14+checkS1T15+checkS1T16+checkS1T17+checkS1T18+checkS1T20'; \
    for flow_class in AdminUserFlowCheckTest \
      AdminLogFlowCheckTest SharedChangePasswordFlowCheckTest SharedRoleProtectionFlowCheckTest \
      AdminProductFlowCheckTest AdminSuperAdminFlowCheckTest AdminOrderFlowCheckTest \
      AdminWarehouseCaseFlowCheckTest AdminDeliveryCaseFlowCheckTest AdminRefundFlowCheckTest \
      AdminDashboardFlowCheckTest; do \
      run_flow \"$flow_class\"; \
    done; \
    awk 'BEGIN { rule=0; flow=0; } \
      /<testsuite / { \
        n=$0; sub(/^.* tests=\"/, \"\", n); sub(/\".*$/, \"\", n); \
        e=$0; sub(/^.* errors=\"/, \"\", e); sub(/\".*$/, \"\", e); \
        f=$0; sub(/^.* failures=\"/, \"\", f); sub(/\".*$/, \"\", f); \
        s=$0; sub(/^.* skipped=\"/, \"\", s); sub(/\".*$/, \"\", s); \
        k=(FILENAME ~ /RuleCheckTest\\.xml$/ ? \"Rule\" : \"Flow\"); \
        pass[k]+=n-e-f-s; fail[k]+=e+f; skip[k]+=s; \
      } \
      END { printf \"Rule checks: %d passed, %d failed, %d skipped\\n\", pass[\"Rule\"], fail[\"Rule\"], skip[\"Rule\"]; \
            printf \"Flow checks: %d passed, %d failed, %d skipped\\n\", pass[\"Flow\"], fail[\"Flow\"], skip[\"Flow\"]; }' \
      target/surefire-reports/TEST-*.xml || shared_flow_status=1; \
    test \"$shared_rule_status$shared_flow_status\" = 00"]

FROM source AS build

RUN ./mvnw -B -DskipTests package

# Run using Java 21
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/target/backend-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]
