package org.fisabilillah.app

import android.app.Application
import org.fisabilillah.app.di.AppGraph

/**
 * The application.
 *
 * Its only job is to build the object graph once. Everything else — the repositories, the
 * use cases, the safety policies — lives in the shared `core:*` modules, which know nothing
 * about Android and are tested without it.
 */
internal class FiSabilillahApplication : Application() {

    val graph: AppGraph by lazy { AppGraph.get(this) }

    override fun onCreate() {
        super.onCreate()
        // Touch the graph eagerly so the seeded development data is ready before the first
        // frame rather than on the first repository call.
        graph.hashCode()
    }
}
