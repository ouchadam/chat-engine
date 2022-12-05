package fake

import app.dapk.engine.core.extensions.ErrorTracker
import io.mockk.mockk

class FakeErrorTracker : ErrorTracker by mockk(relaxed = true)