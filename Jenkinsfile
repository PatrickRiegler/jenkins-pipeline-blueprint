properties([
        buildDiscarder(logRotator(numToKeepStr: '10'))
])
@groovy.transform.Field
def jobContext = [:]
jobContext.secrets = [:]
jobContext.stageName = env.APP_STAGE

echo "Stage name is: ${jobContext.stageName}"
try {
    mavenNode() {
        jobContext.currentBuildVersion = sh(returnStdout: true, script: 'date +%Y%m%d.%H%M%S  -u').trim()

        stage("Maven Build") {
            checkout scm
            this.notifyStash('INPROGRESS')
            mvn "-B versions:set -DnewVersion=${jobContext.currentBuildVersion}"


            try {
                mvn "-B package -DskipUpdateDb"
            } finally {
                processJunitResults()
            }
        }

        parallel(
                "Build db image": {
                    stage("Build db image") {
                        sh 'cp -a target/*/db db-artifacts'
                        sh "oc start-build ${jobContext.stageName}-db --from-dir=db-artifacts"
                        openshiftTag srcStream: "db", srcTag: jobContext.stageName, destStream: "db", destTag: jobContext.currentBuildVersion
                    }

                }, "Build application image": {
            stage("Build application image") {

                sh 'mkdir artifacts'
                sh 'cp target/*/Dockerfile artifacts/Dockerfile'
                sh 'cp target/*.jar artifacts/'

                sh "oc start-build ${jobContext.stageName}-application --from-dir=artifacts"

                openshiftTag srcStream: "application", srcTag: jobContext.stageName, destStream: "application", destTag: jobContext.currentBuildVersion
            }
        })
        parallel(
                "Integration Tests": {
                    stage("Wait for image deployment") {
                        openshiftVerifyDeployment depCfg: jobContext.stageName, replicaCount: '1', verbose: 'false', verifyReplicaCount: 'false', waitTime: '5', waitUnit: 'min'
                    }

                    stage("Integration Tests") {

                    }
                }, "IQ Server": {
            stage("IQ Server") {
                /*
                withCredentials([usernamePassword(credentialsId: 'stash-notifier', passwordVariable: 'IQ_PW', usernameVariable: 'IQ_USER')]) {
                    timestamps {
                        mvn("-B com.sonatype.clm:clm-maven-plugin:evaluate -Dclm.username=${env.IQ_USER} -Dclm.password=${env.IQ_PW}", true)
                    }
                }
                */
            }
        })
    }

    node() {
        this.notifyStash('SUCCESS')
    }
}
catch (any) {
    node() {
        this.notifyStash('FAILURE')
    }
    throw any // rethrow exception to prevent the build from proceeding
}

stage("Install to next Stage") {
    def userInput
    try {
        timeout(2) {

            // get all available deployments
            def availableDeployments
            node() {
                // TODO generic stage instead of oqcbo-stage
                availableDeployments = sh returnStdout: true, script: "oc get dc -l acqbo-stage -o custom-columns=name:.metadata.name --no-headers=true"

                // set qa as default
                if (availableDeployments.contains("qa\n")) {
                    availableDeployments = availableDeployments.replaceAll("qa\n", '')
                    availableDeployments = "qa\n${availableDeployments}"
                }
            }

            userInput = input id: 'NextStage', message: 'Do you want to install this version on the next stage?', parameters: [
                    booleanParam(defaultValue: false, description: 'Install', name: 'install'),
                    choice(choices: availableDeployments, description: 'Stage', name: 'stage')]

            echo "Got user input: ${userInput}"

        }
    } catch (InterruptedException e) {
        // timeout reached do nothing
    }
    if (userInput?.install) {
        node() {
            jobContext.staged = true
            echo "installing version ${jobContext.currentBuildVersion} on next stage ..."
            openshiftTag srcStream: "application", srcTag: jobContext.currentBuildVersion, destStream: "application", destTag: userInput.stage
            openshiftTag srcStream: "db", srcTag: jobContext.currentBuildVersion, destStream: "db", destTag: userInput.stage
        }
    }
}


if (jobContext.staged) {
    stage("Push to Artifactory") {
        try {
            timeout(2) {
                userInput = input id: 'Push', message: 'Do you want to push the image to artifactory?', parameters: [booleanParam(defaultValue: false, description: '', name: 'push'),]

                echo "Got user input: ${userInput}"
                if (userInput) {
                    node {
                        jobContext.applicationImageRegistry = sh returnStdout: true, script: "oc get is application --template='{{ .status.dockerImageRepository }}'"
                        jobContext.dbImageRegistry = sh returnStdout: true, script: "oc get is db --template='{{ .status.dockerImageRepository }}'"
                    }
                }
            }
        } catch (InterruptedException e) {
            // timeout reached do nothing
        }

        if (jobContext.applicationImageRegistry && jobContext.dbImageRegistry) {
            imageMgmtNode() {
                echo "pushing image to artifactory with tag ${jobContext.currentBuildVersion}"
                sh "skopeoCopy.sh -f \"${jobContext.applicationImageRegistry}:${jobContext.currentBuildVersion}\" -t \"artifactory.six-group.net/cbk-mepo/application:${jobContext.currentBuildVersion}\""
                sh "skopeoCopy.sh -f \"${jobContext.dbImageRegistry}:${jobContext.currentBuildVersion}\" -t \"artifactory.six-group.net/cbk-mepo/database:${jobContext.currentBuildVersion}\""

                sh "promoteToArtifactory.sh -i cbk-mepo/application -t ${jobContext.currentBuildVersion} -r  acqbo-docker-release-local"
                sh "promoteToArtifactory.sh -i cbk-mepo/database -t ${jobContext.currentBuildVersion} -r  acqbo-docker-release-local"
            }
        }
    }
}

def mvn(arguments, boolean ignoreError = false) {
    def command = "mvn ${arguments}"
    if (ignoreError) {
        command += " || exit 0"
    }
    echo "${command}"
    sh "${command}"
}

def processJunitResults(boolean allowEmptyResults = false) {
    junit allowEmptyResults: allowEmptyResults, testResults: '**/target/surefire-reports/*.xml,**/target/failsafe-reports/*.xml'
}

def dockerToken(String login = "serviceaccount") {
    node() {
        // Read the auth token from the file defined in the env variable AUTH_TOKEN
        String token = sh returnStdout: true, script: 'cat ${AUTH_TOKEN}'
        String prefix
        if (login) {
            prefix = "${login}:"
        } else {
            prefix = ''
        }
        return prefix + token
    }
}

def mavenNode(Closure body) {
    withCredentials([usernamePassword(credentialsId: 'openshift-cbk-mepo-service-artifactory', passwordVariable: 'ARTIFACTORY_PASSWORD', usernameVariable: 'ARTIFACTORY_USERNAME')]) {
        customNode(body, "mvn-${jobContext.stageName}", 'artifactory.six-group.net/sdbi/jenkins-slave-maven', 'maven', [
                persistentVolumeClaim(claimName: 'maven-repository', mountPath: '/home/jenkins/.m2/repository', readOnly: false)])
    }
}

def imageMgmtNode(Closure body) {
    withCredentials([usernameColonPassword(credentialsId: 'openshift-cbk-mepo-service-artifactory', variable: 'SKOPEO_DEST_CREDENTIALS')]) {
        withEnv(["SKOPEO_SRC_CREDENTIALS=${dockerToken()}", "ARTIFACTORY_BASIC_AUTH=${env.SKOPEO_DEST_CREDENTIALS}"]) {
            customNode(body, "imageMgmt-${jobContext.stageName}", 'artifactory.six-group.net/sdbi/jenkins-slave-image-mgmt', 'maven')
        }
    }
}

def customNode(Closure body, String label, String image, String inheritFrom = '', volumes = []) {
    podTemplate(cloud: 'openshift', inheritFrom: inheritFrom, label: label, name: label,
            volumes: volumes,
            containers: [containerTemplate(
                    name: 'jnlp',
                    image: image,
                    alwaysPullImage: true,
                    args: '${computer.jnlpmac} ${computer.name}',
                    workingDir: '/tmp')]
    ) {
        node(label) {
            body.call()
        }
    }
}


def notifyStash(String state) {
    if (state == 'SUCCESS' || state == 'FAILURE' || state == 'UNSTABLE') {
        currentBuild.result = state         // Set result of currentBuild !Important!
    }

    step([$class: 'StashNotifier'])
}
