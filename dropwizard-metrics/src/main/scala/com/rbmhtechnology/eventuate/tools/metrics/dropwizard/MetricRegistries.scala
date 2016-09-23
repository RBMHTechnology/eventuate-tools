package com.rbmhtechnology.eventuate.tools.metrics.dropwizard

import com.codahale.metrics.Gauge
import com.codahale.metrics.MetricRegistry

object MetricRegistries {

  implicit class RichMetricRegistry(metricRegistry: MetricRegistry) {
    def setGaugeValue[A](name: String, value: A): Unit = {
      val oldEntry = Option(metricRegistry.getGauges().get(name))
      if (oldEntry.forall(_.getValue != value)) {
        metricRegistry.remove(name)
        metricRegistry.register(name, fixValueGauge(value))
      }
    }

    private def fixValueGauge[A](value: A): Gauge[A] = new Gauge[A] {
      override def getValue: A = value
    }
  }
}
