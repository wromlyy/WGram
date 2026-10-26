import base64
from io import BytesIO
import json
import time
import unittest
from unittest.mock import patch

import test_service as helpers
bot = helpers.bot


class CatalogTests(unittest.TestCase):
    setUp = helpers.ServiceTests.setUp
    tearDown = helpers.ServiceTests.tearDown
    request = helpers.ServiceTests.request
    message = helpers.ServiceTests.message
    connect = helpers.ServiceTests.connect

    def create(self, token, title='Мой профиль'):
        self.assertEqual(self.request('/v1/me/profile', {'emoji': 123, 'color': 2,
            'banner': base64.b64encode(helpers.JPEG).decode()}, token)[0], 200)
        status, result = self.request('/v1/customs', {'title': title}, token)
        self.assertEqual(status, 200)
        return result['item']

    def approve(self, custom_id):
        bot.handle_bot_message(self.message(bot.ADMIN, '/approve ' + str(custom_id)))

    def test_submission_pending_owner_and_moderation(self):
        owner, other = self.connect(100), self.connect(101)
        item = self.create(owner)
        self.assertEqual(item['owner'], 100)
        self.assertEqual(item['status'], 'pending')
        self.assertEqual(self.request('/v1/customs', token=other)[1]['items'], [])
        self.assertEqual(self.request('/v1/customs/'+str(item['id']), token=other)[0], 404)
        self.assertEqual(self.request('/v1/customs?mine=1', token=owner)[1]['items'][0]['id'], item['id'])
        bot.handle_bot_message(self.message(101, '/approve '+str(item['id'])))
        self.assertEqual(self.request('/v1/customs', token=other)[1]['items'], [])
        self.approve(item['id'])
        self.assertEqual(self.request('/v1/customs', token=other)[1]['items'][0]['id'], item['id'])

    def test_immutable_snapshot_and_delete_ownership(self):
        owner, other = self.connect(100), self.connect(101)
        item = self.create(owner)
        self.request('/v1/me/profile', {'emoji': 456, 'banner': ''}, owner)
        path='/v1/customs/'+str(item['id'])
        self.assertEqual(self.request(path, token=owner)[1]['item']['payload']['emoji'], 123)
        self.assertEqual(self.request(path+'/banner', token=owner), (200,helpers.JPEG))
        self.approve(item['id'])
        self.assertEqual(self.request(path+'/delete', {}, other)[0], 403)
        self.assertEqual(self.request(path+'/delete', {}, owner)[0], 200)
        self.assertEqual(self.request(path, token=other)[0], 404)
        self.assertEqual(self.request(path+'/banner', token=owner)[0], 404)

    def test_forged_owner_ignored_and_duplicates_idempotent(self):
        token=self.connect(100)
        self.request('/v1/me/profile', {'emoji': 123}, token)
        first=self.request('/v1/customs', {'title':'A','owner':101,'status':'published'}, token)[1]['item']
        second=self.request('/v1/customs', {'title':'A'}, token)[1]['item']
        self.assertEqual(first['id'],second['id'])
        self.assertEqual(first['owner'],100)
        self.assertEqual(first['status'],'pending')
        self.assertEqual(len(self.request('/v1/customs?mine=1',token=token)[1]['items']),1)

    def test_submission_validation_and_limits(self):
        token=self.connect(100)
        self.assertEqual(self.request('/v1/customs', {'title':'Empty'}, token)[0],400)
        for payload in [{'title':''},{'title':'x'*61},{'kind':'exe','title':'No'},
                        {'kind':'emoji','title':'Emoji','emoji':-1},{'kind':'emoji','title':'Emoji','emoji':True}]:
            self.assertEqual(self.request('/v1/customs', payload, token)[0],400)
        self.request('/v1/me/profile', {'emoji':123}, token)
        with patch.object(bot,'MAX_CUSTOMS_PER_USER',1):
            self.create(token)
            self.assertEqual(self.request('/v1/customs', {'title':'Other'}, token)[0],429)
        with patch.object(bot,'MAX_CUSTOMS',1):
            other=self.connect(101)
            self.request('/v1/me/profile', {'emoji':124}, other)
            self.assertEqual(self.request('/v1/customs', {'title':'Other'}, other)[0],429)

    def test_pagination_filters_and_emoji_64bit(self):
        token=self.connect(100)
        self.request('/v1/me/profile', {'emoji':123}, token)
        ids=[]
        for index in range(23):
            item=self.request('/v1/customs', {'title':str(index),'kind':'emoji','emoji':9000000000000000000},token)[1]['item']
            ids.append(item['id']);self.approve(item['id'])
        first=self.request('/v1/customs?kind=emoji',token=token)[1]
        second=self.request('/v1/customs?before='+str(first['next'])+'&kind=emoji',token=token)[1]
        self.assertEqual(len(first['items']),20)
        self.assertEqual(len(second['items']),3)
        self.assertEqual(second['next'],0)
        self.assertEqual([row['id'] for row in first['items']+second['items']],list(reversed(ids)))
        self.assertEqual(first['items'][0]['payload']['emoji'],9000000000000000000)
        self.assertEqual(self.request('/v1/customs?kind=banner',token=token)[1]['items'],[])

    def test_photo_catalog_upload_explicit_and_separate_from_profile(self):
        token=self.connect(100)
        with patch.object(bot.urllib.request,'urlopen',return_value=BytesIO(helpers.JPEG)):
            bot.handle_bot_message(self.message(100,photo=[{'file_id':'abc','file_size':len(helpers.JPEG)}],caption='/custom Sunset'))
        self.assertFalse(self.request('/v1/profiles/100',token=token)[1]['banner'])
        item=self.request('/v1/customs?mine=1',token=token)[1]['items'][0]
        self.assertEqual(item['kind'],'banner')
        self.assertEqual(item['title'],'Sunset')
        self.assertEqual(self.request('/v1/customs/'+str(item['id'])+'/banner',token=token)[1],helpers.JPEG)

    def test_forwarded_group_and_old_submissions_ignored(self):
        token=self.connect(100)
        text='/emoji 123 Example'
        for extra in [{'forward_origin':{}},{'date':0},{'chat':{'id':-123,'type':'supergroup'}}]:
            bot.handle_bot_message(self.message(100,text,**extra))
        self.assertEqual(self.request('/v1/customs?mine=1',token=token)[1]['items'],[])
        bot.handle_bot_message(self.message(100,text))
        self.assertEqual(len(self.request('/v1/customs?mine=1',token=token)[1]['items']),1)

    def test_custom_emoji_can_be_sent_without_knowing_its_id(self):
        token=self.connect(100)
        bot.handle_bot_message(self.message(100,'😀',entities=[{'type':'custom_emoji','offset':0,'length':2,'custom_emoji_id':'9000000000000000000'}]))
        item=self.request('/v1/customs?mine=1',token=token)[1]['items'][0]
        self.assertEqual(item['kind'],'emoji')
        self.assertEqual(item['payload']['emoji'],9000000000000000000)
        self.assertEqual(item['status'],'pending')
        bot.handle_bot_message(self.message(100,'😀',entities=[{'type':'custom_emoji','custom_emoji_id':'../invalid'}]))
        self.assertEqual(len(self.request('/v1/customs?mine=1',token=token)[1]['items']),1)

    def test_pack_verified_and_no_arbitrary_url_download(self):
        token=self.connect(100)
        self.mock_api.return_value={'title':'Emoji set','sticker_type':'custom_emoji'}
        bot.handle_bot_message(self.message(100,'https://t.me/addemoji/Example_by_bot'))
        self.assertIn(unittest.mock.call('getStickerSet',{'name':'Example_by_bot'}),self.mock_api.call_args_list)
        item=self.request('/v1/customs?mine=1',token=token)[1]['items'][0]
        self.assertEqual(item['kind'],'emoji_pack')
        before=self.mock_api.call_count
        bot.handle_bot_message(self.message(100,'https://example.org/addemoji/Example'))
        self.assertEqual(self.mock_api.call_count,before+1) # Help reply only.
        self.assertEqual(len(self.request('/v1/customs?mine=1',token=token)[1]['items']),1)
        bot.handle_bot_message(self.message(100,'https://t.me/addstickers/Example_by_bot'))
        self.assertEqual(len(self.request('/v1/customs?mine=1',token=token)[1]['items']),1)

    def test_rejected_item_hidden_and_can_be_removed(self):
        token=self.connect(100);item=self.create(token)
        bot.handle_bot_message(self.message(bot.ADMIN,'/reject '+str(item['id'])))
        self.assertEqual(self.request('/v1/customs',token=token)[1]['items'],[])
        self.assertEqual(self.request('/v1/customs?mine=1',token=token)[1]['items'][0]['status'],'rejected')
        bot.handle_bot_message(self.message(101,'/remove '+str(item['id'])))
        self.assertEqual(len(self.request('/v1/customs?mine=1',token=token)[1]['items']),1)
        bot.handle_bot_message(self.message(100,'/remove '+str(item['id'])))
        self.assertEqual(self.request('/v1/customs?mine=1',token=token)[1]['items'],[])

    def test_optional_group_publication_only_after_approval_and_once(self):
        token=self.connect(100)
        with patch.object(bot,'CATALOG_CHAT','-1001234567890'):
            item=self.create(token)
            bot.deliver_catalog()
            self.assertFalse(any(call.args[1].get('chat_id')=='-1001234567890' for call in self.mock_api.call_args_list))
            self.approve(item['id']);self.approve(item['id'])
            bot.deliver_catalog();bot.deliver_catalog()
        posts=[call for call in self.mock_api.call_args_list if call.args[0]=='sendMessage' and call.args[1].get('chat_id')=='-1001234567890']
        self.assertEqual(len(posts),1)
        self.assertIn('?start=custom_'+str(item['id']),posts[0].args[1]['text'])

    def emoji_item(self):
        with bot.database() as db:
            item = bot.create_custom(db, 100, 'emoji', 'Animated emoji', {'emoji': 9000000000000000000})
            bot.moderate_custom(db, bot.ADMIN, item['id'], True)
        return item

    def emoji_sticker(self, **extra):
        return dict({'type': 'custom_emoji', 'custom_emoji_id': '9000000000000000000',
            'emoji': '😀', 'file_id': 'full', 'is_animated': True,
            'thumbnail': {'file_id': 'thumb'}}, **extra)

    def test_native_emoji_visible_to_recipient_without_premium(self):
        item = self.emoji_item()
        def telegram(method, data):
            if method == 'getCustomEmojiStickers':
                return [self.emoji_sticker()]
            return {'entities': data.get('entities', [])}
        self.mock_api.side_effect = telegram
        with patch.object(bot, 'api_upload') as upload:
            bot.handle_bot_message(self.message(101, '/view ' + str(item['id']), **{'from': {'id': 101, 'is_premium': False}}))
        native = [call.args[1] for call in self.mock_api.call_args_list if call.args[1].get('entities')]
        self.assertEqual(len(native), 1)
        self.assertEqual(native[0]['chat_id'], 101)
        self.assertEqual(native[0]['entities'][0]['length'], 2)  # UTF-16, not Python code points.
        self.assertEqual(native[0]['entities'][0]['custom_emoji_id'], '9000000000000000000')
        upload.assert_not_called()

    def test_bot_permission_denied_or_entity_stripped_uses_real_picture(self):
        from PIL import Image
        source = BytesIO()
        Image.new('RGBA', (100, 100), (255, 0, 0, 255)).save(source, 'WEBP')
        for rejected in (False, True):
            def telegram(method, data):
                if method == 'getCustomEmojiStickers':
                    return [self.emoji_sticker()]
                if method == 'getFile':
                    self.assertEqual(data['file_id'], 'thumb')
                    return {'file_path': 'stickers/preview.webp'}
                if rejected:
                    raise RuntimeError('CUSTOM_EMOJI_NOT_ALLOWED')
                return {'entities': []}  # Telegram can strip disallowed entities silently.
            self.mock_api.side_effect = telegram
            with patch.object(bot.urllib.request, 'urlopen', return_value=BytesIO(source.getvalue())), patch.object(bot, 'api_upload') as upload:
                self.assertTrue(bot.send_emoji_preview(101, 9000000000000000000))
            method, payload, field, name, image = upload.call_args.args
            self.assertEqual((method, payload['chat_id'], field, name), ('sendPhoto', 101, 'photo', 'emoji.png'))
            self.assertNotIn('entities', payload)
            self.assertNotIn('api.telegram.org', json.dumps(payload))
            with Image.open(BytesIO(image)) as decoded:
                self.assertEqual(decoded.size, (320, 320))
                self.assertGreater(decoded.getpixel((160, 160))[0], 240)
                self.assertLess(decoded.getpixel((160, 160))[1], 10)

    def test_pending_emoji_not_sent_to_other_user_even_by_view_command(self):
        with bot.database() as db:
            item = bot.create_custom(db, 100, 'emoji', 'Private pending', {'emoji': 123})
        self.mock_api.reset_mock()
        bot.handle_bot_message(self.message(101, '/view ' + str(item['id'])))
        self.assertEqual(self.mock_api.call_count, 1)
        self.assertEqual(self.mock_api.call_args.args[1]['text'], 'Кастомка не найдена.')

    def test_emoji_deep_link_previews_without_pairing_or_subscription(self):
        item = self.emoji_item()
        with patch.object(bot, 'send_emoji_preview', return_value=True) as preview:
            bot.handle_bot_message(self.message(101, '/start custom_' + str(item['id'])))
        preview.assert_called_once_with(101, 9000000000000000000)

    def test_unavailable_emoji_does_not_claim_success(self):
        self.mock_api.side_effect = lambda method, data: [] if method == 'getCustomEmojiStickers' else {}
        with patch.object(bot, 'api_upload') as upload:
            self.assertFalse(bot.send_emoji_preview(101, 123))
        upload.assert_not_called()
        self.assertIn('недоступен', self.mock_api.call_args.args[1]['text'])

    def test_preview_download_rejects_path_escape_and_oversize(self):
        for info in ({'file_path': '../secret'}, {'file_path': 'https://example.org/image'},
                     {'file_path': '/secret'}, {'file_path': 'a.webp', 'file_size': 1_500_001}):
            self.mock_api.return_value = info
            with patch.object(bot.urllib.request, 'urlopen') as fetch:
                with self.assertRaises(ValueError):
                    bot.download_emoji_image('thumb')
            fetch.assert_not_called()
        self.mock_api.return_value = {'file_path': 'a.webp'}
        with patch.object(bot.urllib.request, 'urlopen', return_value=BytesIO(b'x' * 1_500_001)):
            with self.assertRaises(ValueError):
                bot.download_emoji_image('thumb')

    def test_static_and_repainting_preview_and_dimensions(self):
        from PIL import Image
        for size, rejected in (((100, 100), False), ((513, 100), True)):
            source = BytesIO()
            Image.new('RGBA', size, (255, 255, 255, 255)).save(source, 'PNG')
            sticker = self.emoji_sticker(is_animated=False, needs_repainting=True)
            with patch.object(bot, 'download_emoji_image', return_value=source.getvalue()) as download:
                if rejected:
                    with self.assertRaises(ValueError):
                        bot.emoji_preview_png(sticker)
                else:
                    with Image.open(BytesIO(bot.emoji_preview_png(sticker))) as image:
                        self.assertEqual(image.getpixel((160, 160)), (40, 40, 40))
                download.assert_called_once_with('full')

    def test_multipart_preview_upload_contains_bytes_and_no_token_in_payload(self):
        response = BytesIO(json.dumps({'ok': True, 'result': {'message_id': 1}}).encode())
        with patch.object(bot.urllib.request, 'urlopen', return_value=response) as post:
            self.assertEqual(bot.api_upload('sendPhoto', {'chat_id': 101}, 'photo', 'emoji.png', b'PNG_BYTES'), {'message_id': 1})
        request = post.call_args.args[0]
        self.assertIn(b'filename="emoji.png"', request.data)
        self.assertIn(b'PNG_BYTES', request.data)
        self.assertIn('multipart/form-data', request.headers['Content-type'])

    def test_public_link_does_not_expose_pending_and_admin_list(self):
        token=self.connect(100);item=self.create(token)
        self.assertEqual(bot.handle_custom_command(101,'/start custom_'+str(item['id'])),'Кастомка не найдена.')
        self.assertIn(item['title'],bot.handle_custom_command(100,'/start custom_'+str(item['id'])))
        self.assertNotIn(item['title'],bot.handle_custom_command(101,'/pending'))
        self.assertIn(item['title'],bot.handle_custom_command(bot.ADMIN,'/pending'))
        self.approve(item['id'])
        self.assertIn(item['title'],bot.handle_custom_command(101,'/start custom_'+str(item['id'])))


if __name__ == '__main__':
    unittest.main()
