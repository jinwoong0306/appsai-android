import { createChatHandler } from "./chat.js";
import { verifyAppCheck } from "./app-check.js";

export default {
  async fetch(request, env) {
    const handler = createChatHandler({
      verifyToken: (token) => verifyAppCheck(token,
        env.FIREBASE_PROJECT_NUMBER, env.FIREBASE_APP_ID),
      getAppId: () => env.FIREBASE_APP_ID,
      getApiKey: () => env.OPENAI_API_KEY,
    });
    return handler(request);
  },
};
