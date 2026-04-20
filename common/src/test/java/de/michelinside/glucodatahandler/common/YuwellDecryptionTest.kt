package de.michelinside.glucodatahandler.common

import de.michelinside.glucodatahandler.common.tasks.yuwell.encryption.AESTools
import org.json.JSONObject
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64 // Import the Java standard Base64

class YuwellDecryptionTest {

    val request = """
{
  "body": "V5wTsh287P2Y+N/aNioM32613yYfLefgBaWnEiBU0hPO54/P5CBhHaNf9EGPyfEwkVGhNoYO6Wbp\not6UkjKone5pB2n0bTZh/atOoWm2Ghg=\n",
  "id": 13,
  "key": "3H9mHYoLVfuPQiedo3Cn7bXUbvzm/+hVSff+wimdnmY=\n"
}
    """

    val response = """
{
  "id": 15,
  "body": "VZycDXOzQPoUd7E2HHmwxLvbvaeOPjgfp2zWz4BXeg+b4lFJ8rqEVD1X1vharN3TYF7JSwbyP9CDBeukFkRxw1ulqdtXmxcFEcouveGPa4fhceJdYGrSaOVZRThH4ylS",
  "key": "Wnbx3IOsUn0n1EZO5/tKbKL6u9bSipLyYIA3f7t+eVw="
}
    """

    val sensitivData = mutableSetOf("password", "userUID", "refreshToken", "userId", "userEmail", "email", "clientId", "clientSecret", "mac", "userIdSubscribe")

    private fun replaceSensitiveData(body: String?): String? {
        try {
            if(body.isNullOrEmpty())
                return body
            var result = body
            sensitivData.forEach {
                val groups = Regex("\"$it\":\"(.*?)\"").find(result)?.groupValues
                if(!groups.isNullOrEmpty() && groups.size > 1 && groups[1].isNotEmpty()) {
                    val replaceValue = groups[0].replace(groups[1], "---")
                    result = result.replace(groups[0], replaceValue)
                }
            }
            return result
        } catch (_: Exception) {
            return body
        }
    }
    @Test
    fun testDecryptRequest() {

        val jsonObject = JSONObject(request.trimIndent())

        assertNotNull("The JSON object should not be null", jsonObject)

        assertTrue("The JSON object should have a 'body' key", jsonObject.has("body"))
        assertTrue("The JSON object should have a 'key' key", jsonObject.has("key"))

        val decryptedBody = jsonObject.getString("body").replace("\n", "")
        val decryptedKey = jsonObject.getString("key").replace("\n", "")

        // Use java.util.Base64.getDecoder() instead of android.util.Base64
        val data = Base64.getDecoder().decode(decryptedBody)
        val key = Base64.getDecoder().decode(decryptedKey)

        val result = AESTools.parse(data, key)

        println("Decrypted Request Result:")
        println(replaceSensitiveData(result))

        assertNotNull("The decryption result should not be null", result)
    }

    @Test
    fun testDecryptResponse() {

        val jsonObject = JSONObject(response.trimIndent())

        assertNotNull("The JSON object should not be null", jsonObject)

        assertTrue("The JSON object should have a 'body' key", jsonObject.has("body"))
        assertTrue("The JSON object should have a 'key' key", jsonObject.has("key"))

        val decryptedBody = jsonObject.getString("body").replace("\n", "")
        val decryptedKey = jsonObject.getString("key").replace("\n", "")

        // Use java.util.Base64.getDecoder() here as well
        val data = Base64.getDecoder().decode(decryptedBody)
        val key = Base64.getDecoder().decode(decryptedKey)

        val result = AESTools.parse(data, key)

        println("Decrypted Response Result:")
        println(replaceSensitiveData(result))

        assertNotNull("The decryption result should not be null", result)
    }
}