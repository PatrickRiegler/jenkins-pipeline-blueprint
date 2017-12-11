FROM artifactory.six-group.net/sdbi/openjdk-headless-8
USER 1001
LABEL six.project=blueprint \
      six.department=ocp

ENV APPLICATION_HOME=/opt/blueprint/

WORKDIR ${APPLICATION_HOME}

EXPOSE 8080
CMD java $(/opt/run-java/java-default-options) ${JAVA_OPTIONS} -jar /opt/blueprint/blueprint.jar ${APP_OPTIONS}

COPY blueprint.jar ${APPLICATION_HOME}/blueprint.jar

LABEL six.ocp.blueprint.version="@project.version@" \
      six.ocp.blueprint.git.revision="@git.revision@" \
      six.version="@project.version@"

ENV BLUEPRINT_VERSION="@project.version@" \
    BLUEPRINT_GIT_REVISION="@git.revision@"

