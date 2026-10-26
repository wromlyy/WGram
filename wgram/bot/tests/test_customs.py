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
