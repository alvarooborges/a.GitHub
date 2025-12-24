package net.hyren.github.application.misc.build

import com.redefantasy.core.shared.CoreProvider
import com.redefantasy.core.shared.echo.packets.project.ProjectFailedBuildEchoPacket
import com.redefantasy.core.shared.echo.packets.project.ProjectSuccessBuildEchoPacket
import net.hyren.github.application.frameworks.Framework
import net.hyren.github.application.frameworks.implementations.GradleFramework
import net.hyren.github.application.frameworks.implementations.MavenFramework
import org.apache.maven.shared.invoker.*
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ResultHandler
import java.io.File

/**
 * @author Gutyerrez and Alvaro Borges
 */
class Build(
    val id: Int,
    val framework: Framework,
    vararg val parameters: Any?
) {

    fun start() {

        if (framework is GradleFramework) {

            val connector = parameters[0] as GradleConnector
            val tasks = parameters.copyOfRange(1, parameters.size)
                .map { it.toString() }
                .toTypedArray()

            val projectConnection = connector.connect()
            val buildLauncher = projectConnection.newBuild()

            buildLauncher.forTasks(*tasks)

            buildLauncher.run(object : ResultHandler<Void> {

                private val startTime = System.currentTimeMillis()

                init {
                    println("Gradle Build $id")
                    framework.onStart(id)
                }

                override fun onComplete(result: Void?) {
                    val packet = ProjectSuccessBuildEchoPacket(
                        id,
                        System.currentTimeMillis() - startTime
                    )

                    CoreProvider.Databases.Redis.ECHO.provide()
                        .publishToAll(packet)

                    projectConnection.close()
                }

                override fun onFailure(failure: GradleConnectionException?) {
                    val packet = ProjectFailedBuildEchoPacket(
                        id,
                        failure?.message
                    )

                    CoreProvider.Databases.Redis.ECHO.provide()
                        .publishToAll(packet)

                    projectConnection.close()
                }
            })

        } else if (framework is MavenFramework) {

            val projectDir = parameters[0] as File
            val goals = parameters.copyOfRange(1, parameters.size)
                .map { it.toString() }

            val startTime = System.currentTimeMillis()

            println("Maven Build $id")
            framework.onStart(id)

            val request = DefaultInvocationRequest().apply {
                pomFile = File(projectDir, "pom.xml")
                setGoals(goals)
                isBatchMode = true
            }

            val invoker: Invoker = DefaultInvoker()

            try {
                val result = invoker.execute(request)

                if (result.exitCode == 0) {
                    val packet = ProjectSuccessBuildEchoPacket(
                        id,
                        System.currentTimeMillis() - startTime
                    )

                    CoreProvider.Databases.Redis.ECHO.provide()
                        .publishToAll(packet)
                } else {
                    val packet = ProjectFailedBuildEchoPacket(
                        id,
                        "Maven build failed with exit code ${result.exitCode}"
                    )

                    CoreProvider.Databases.Redis.ECHO.provide()
                        .publishToAll(packet)
                }

            } catch (ex: MavenInvocationException) {
                val packet = ProjectFailedBuildEchoPacket(
                    id,
                    ex.message
                )

                CoreProvider.Databases.Redis.ECHO.provide().publishToAll(packet)
            }
        }
    }
}
