// Keeps the Java server on Render's free plan awake. It sleeps after 15 minutes without a request, and waking it on
// that plan's sliver of a processor takes minutes (ADR-0015); a ping every 10 minutes keeps it up. One always-on
// service uses about 744 of the 750 free hours Render gives a month.
// The address is the one netlify.toml sends /api requests to.
const PING = 'https://ozecmaps-api.onrender.com/api/ping';

export default async () => {
    try {
        const response = await fetch(PING, { signal: AbortSignal.timeout(25000) });
        console.log(`ping: HTTP ${response.status}`);
    } catch (error) {
        // a server still waking up answers late; the next ping finds it up
        console.log(`ping failed: ${error.message}`);
    }
};

export const config = {
    schedule: '*/10 * * * *'
};
