package com.syncfusion

object Driver {

  def main(args: Array[String]): Unit = {

    var sum = 0

    for (i <- 1 to 5) {
      val thread = new Thread {
        override def run {
          for (i <- 0 to 100){
            sum += i
          }
          
          println(s"Thread ID: ${Thread.currentThread().getId} has finished running.")  
        }
      }

      thread.start
    }
    
    Thread.sleep(2000)
    println(s"Sum is $sum")
  }
}