package com.maslak.gps.mock

import com.maslak.gps.data.Geo
import com.maslak.gps.data.LatLng
import com.maslak.gps.data.MockMode
import com.maslak.gps.data.MockRequest
import com.maslak.gps.data.MockSettings
import kotlin.math.sqrt
import kotlin.random.Random

data class Sample(val point: LatLng, val bearing: Float, val speed: Float)

interface Mover {
    fun step(dtSec: Double, s: MockSettings): Sample

    companion object {
        fun create(req: MockRequest): Mover = when (req.mode) {
            MockMode.FIXED -> FixedMover(req.center)
            MockMode.ROUTE -> if (req.route.size >= 2) RouteMover(req.route, req.loop) else FixedMover(req.center)
            MockMode.RANDOM -> RandomMover(req.center)
        }
    }
}

class FixedMover(private val p: LatLng) : Mover {
    override fun step(dtSec: Double, s: MockSettings) = Sample(p, 0f, 0f)
}

/** يتحرك على طول المسار بسرعة ثابتة، مع إمكانية التكرار */
class RouteMover(points: List<LatLng>, private val loop: Boolean) : Mover {
    private val pts: List<LatLng> = if (loop && points.first() != points.last()) points + points.first() else points
    private var seg = 0
    private var along = 0.0
    private var lastBearing = Geo.bearing(pts[0], pts[1]).toFloat()

    override fun step(dtSec: Double, s: MockSettings): Sample {
        val speed = s.speedKmh / 3.6
        var remaining = speed * dtSec
        var finished = false
        while (remaining > 0) {
            val len = Geo.distance(pts[seg], pts[seg + 1])
            val left = len - along
            if (remaining < left) {
                along += remaining
                remaining = 0.0
            } else {
                remaining -= left
                seg++
                along = 0.0
                if (seg >= pts.size - 1) {
                    if (loop) seg = 0 else {
                        seg = pts.size - 2
                        along = Geo.distance(pts[seg], pts[seg + 1])
                        finished = true
                        break
                    }
                }
            }
        }
        val a = pts[seg]
        val b = pts[seg + 1]
        if (Geo.distance(a, b) > 0.01) lastBearing = Geo.bearing(a, b).toFloat()
        val p = Geo.destination(a, lastBearing.toDouble(), along)
        return Sample(p, lastBearing, if (finished) 0f else speed.toFloat())
    }
}

/** حركة عشوائية طبيعية داخل دائرة حول المركز */
class RandomMover(private val center: LatLng) : Mover {
    private var pos = center
    private var target = center
    private var pauseLeft = 0.0
    private var bearing = 0f

    private fun newTarget(radius: Double): LatLng {
        val r = radius * sqrt(Random.nextDouble())
        return Geo.destination(center, Random.nextDouble(0.0, 360.0), r)
    }

    override fun step(dtSec: Double, s: MockSettings): Sample {
        if (pauseLeft > 0) {
            pauseLeft -= dtSec
            return Sample(pos, bearing, 0f)
        }
        val speed = s.speedKmh / 3.6
        val dist = Geo.distance(pos, target)
        val move = speed * dtSec
        if (dist <= move || dist < 0.5) {
            pos = target
            target = newTarget(s.randomRadius.toDouble())
            if (Random.nextFloat() < 0.3f) pauseLeft = Random.nextDouble(2.0, 8.0)
        } else {
            bearing = Geo.bearing(pos, target).toFloat()
            pos = Geo.destination(pos, bearing.toDouble(), move)
        }
        return Sample(pos, bearing, speed.toFloat())
    }
}
