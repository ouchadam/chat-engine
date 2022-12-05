package fake

import app.dapk.engine.core.JobBag
import io.mockk.mockk

class FakeJobBag {
    val instance = mockk<JobBag>()
}

