package com.syncfusion

import akka.actor._
import akka.routing._

import java.io._
import scala.io.Source

case class ProcessDirectory(name: String)
case class ProcessFile(name: String)
case class ReportIntermediateData(list: List[(String, Int)])
case class ProcessGroup(beverage: String, list: List[(String, Int)])
case class ReportSummary(beverage: String, averageCups: Double)

class FileProcessor() extends Actor() {

  def receive = {
    case ProcessFile(name) =>
      println(s"Processing $name")
      var lst: List[(String, Int)] = Nil
      for (line <- Source.fromFile(name).getLines() if !line.trim().isEmpty()) {
        val parts = line.split(",")
        lst = (parts(1), parts(2).trim().toInt) :: lst
      }

      sender ! ReportIntermediateData(lst)
  }
}

class Aggregator() extends Actor() {
  var pending = 0
  var completeData: List[(String, Int)] = Nil
  val start = System.nanoTime()

  val processorRef = context.system.actorOf(RoundRobinPool(100).props(Props[FileProcessor]))

  def receive = {
    case ProcessDirectory(name) =>
       val children = new File(name).listFiles()
      if (children != null) {
        children.filter ( !_.isDirectory() )
          .foreach {
            f => processorRef ! ProcessFile(f.getAbsolutePath)
            pending += 1
        }
      }

    case ReportIntermediateData(lst) =>
      pending = pending - 1
      completeData = lst ::: completeData

      if (pending == 0) {
        println("Obtained intermediate data. Starting summary processing...")
        val groupedByBeverage = completeData.groupBy(t => t._1)

        val summarizerRef = context.system.actorOf(RoundRobinPool(10).props(Props[Summarizer]))
        groupedByBeverage.foreach{case (beverage, lst) => {
          summarizerRef ! ProcessGroup(beverage, lst)
          pending += 1
        }}
      }

    case ReportSummary(beverage, averageCups) =>
      pending -= 1
      println(f"Average cups consumed by $beverage drinkers is $averageCups%2.4f")
      if (pending == 0) {
        context.system.shutdown()
        val end = System.nanoTime()
        println(s"Total time taken is ${(end - start) / 1.0e9} seconds")
        println(s"${completeData.length} items found")
      }
  }
}

class Summarizer() extends Actor() {

  def receive = {
    case ProcessGroup(beverage, listOfCupCounts) =>
      var sum = 0.0
      for ((_, cupCount) <- listOfCupCounts)
        sum += cupCount

      val cupsPerDay = sum / listOfCupCounts.length

      sender ! ReportSummary(beverage, cupsPerDay)
  }
}

object Driver {

  def main(args: Array[String]) = {
    val appPath = new File(System.getProperty("user.dir")).getParentFile().getAbsolutePath()
    val dataPath = new File(appPath, "data").getAbsolutePath()
    
    val system = ActorSystem("Aggregator")
    val act = system.actorOf(Props[Aggregator])
    act ! ProcessDirectory(dataPath)
  }

}