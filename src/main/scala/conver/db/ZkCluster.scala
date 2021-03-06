package conver.db

import com.github.dockerjava.core.command.PullImageResultCallback
import com.typesafe.scalalogging.LazyLogging

object ZkCluster extends Cluster with LazyLogging {

  val netName = "zk"
  val zkDockerImage = "pviotti/zookeeper:latest"

  def start(num: Int): Array[String] = {

    // TODO check and handle ConflictException in case network
    // or containers are already there
    //val info = docker.infoCmd().exec()
    //println(docker.infoCmd().exec())

    pullDockerImage(zkDockerImage)

    var containers = Array.ofDim[String](num)
    val network = docker.createNetworkCmd().withName(netName).exec()

    val sb = StringBuilder.newBuilder
    sb.append("SERVERS=")
    for (i <- 1 to num)
      if (i < num) sb.append("zookeeper" + i + ",")
      else sb.append("zookeeper" + i)
    val servers = sb.toString

    for (i <- 1 to num) {
      val container = docker
        .createContainerCmd(zkDockerImage)
        .withName("zookeeper" + i)
        .withHostName("zookeeper" + i)
        .withEnv("MYID=" + i, servers)
        .withNetworkMode(netName)
        .exec()
      docker.startContainerCmd(container.getId).exec()

      val netInfo =
        docker.inspectNetworkCmd().withNetworkId(network.getId).exec()
      val ipAddr = netInfo.getContainers.get(container.getId).getIpv4Address
      logger.info("Server zk" + i + " started: " + ipAddr)

      containers(i - 1) = container.getId
    }

    containers
  }

  def getConnectionString(cIds: Array[String]): String = {
    val sb = StringBuilder.newBuilder
    for (i <- 0 until cIds.length) {
      val netInfo = docker.inspectNetworkCmd().withNetworkId(netName).exec()
      var ipAddr = netInfo.getContainers.get(cIds(i)).getIpv4Address
      if (i != cIds.length - 1)
        sb.append(ipAddr.substring(0, ipAddr.lastIndexOf('/')) + ":2181,")
      else
        sb.append(ipAddr.substring(0, ipAddr.lastIndexOf('/')) + ":2181")
    }
    sb.toString()
  }

  override def stop(cIds: Array[String]) = {
    super.stop(cIds)
    docker.removeNetworkCmd(netName).exec()
  }

  //  def printState(cIds: Array[String]) = {
  //    for (cId <- cIds) {
  //      val inspect = docker.inspectContainerCmd(cId).exec()
  //      println(inspect)
  //    }
  //  }
  //  def main(arg: Array[String]): Unit = {
  //      var contIds = start(3)
  //      slowDownNetwork(contIds)
  //      //printState(contIds)
  //      Thread.sleep(2000)
  //      getConnectionString(contIds)
  //      stop(contIds)
  //  }
}
