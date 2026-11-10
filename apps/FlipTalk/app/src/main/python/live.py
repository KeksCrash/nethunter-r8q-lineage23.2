import asyncio
from TikTokLive import TikTokLiveClient
from TikTokLive.events import CommentEvent, LikeEvent, GiftEvent, FollowEvent

_client = None

def run(username, callback):
    global _client
    _client = TikTokLiveClient(unique_id=username.lstrip('@'))
    @_client.on(CommentEvent)
    async def comment(e): callback.onEvent(0, str(e.user.unique_id), str(e.comment))
    @_client.on(LikeEvent)
    async def like(e): callback.onEvent(1, str(e.user.unique_id), str(getattr(e,'count',1)))
    @_client.on(GiftEvent)
    async def gift(e): callback.onEvent(2, str(e.user.unique_id), str(e.gift.name))
    @_client.on(FollowEvent)
    async def follow(e): callback.onEvent(3, str(e.user.unique_id), 'Follow')
    try: _client.run()
    except Exception as exc: callback.onError(str(exc))

def stop():
    global _client
    if _client:
        try: asyncio.run(_client.disconnect())
        except Exception: pass
