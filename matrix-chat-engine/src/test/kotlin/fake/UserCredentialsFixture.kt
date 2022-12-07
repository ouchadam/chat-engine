package fake

import app.dapk.st.matrix.common.DeviceId
import app.dapk.st.matrix.common.HomeServerUrl
import app.dapk.st.matrix.common.UserCredentials
import app.dapk.st.matrix.common.UserId
import fixture.aUserId

fun aUserCredentials(
    accessToken: String = "an-access-token",
    homeServer: HomeServerUrl = HomeServerUrl("homserver-url"),
    userId: UserId = aUserId(),
    deviceId: DeviceId = aDeviceId(),
) = UserCredentials(accessToken, homeServer, userId, deviceId)

fun aDeviceId(value: String = "a-device-id") = DeviceId(value)
