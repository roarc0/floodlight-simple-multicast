#!/usr/bin/env python3
import sys, os, socket, struct, time

sockR = None
sockS = None

NORMAL=''
GREEN='\033[92m'
RED='\033[91m'
YELLOW='\033[93m'
ENDC='\033[0m'

def colorPrint(type, message):
	print(type + message + ENDC)

def multicastJoin(sock, groupAddr):
	mreq = struct.pack("4sl", socket.inet_aton(groupAddr), socket.INADDR_ANY)
	sock.setsockopt(socket.IPPROTO_IP, socket.IP_ADD_MEMBERSHIP, mreq)

def multicastLeave(sock, groupAddr):
	mreq = struct.pack("4sl", socket.inet_aton(groupAddr), socket.INADDR_ANY)
	sock.setsockopt(socket.IPPROTO_IP, socket.IP_DEL_MEMBERSHIP, mreq)

def multicastRecv(groupAddr, groupPort, join, count):
	global sockR
	if sockR is None:
		colorPrint(YELLOW, "*** creating recv socket ***")
		sockR = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
		sockR.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
		sockR.bind((groupAddr, groupPort))  # use groupAddr instead of '' to listen only

		if join:
			multicastJoin(sockR, groupAddr)

	while count>0:
		colorPrint(RED, "recv from " + groupAddr  + ":" + str(groupPort) +  " < \"" + str(sockR.recv(10240),'utf-8') + "\"" )
		count-=1

def multicastSend(groupAddr, groupPort, join, count, message, sleepTime):
	global sockS
	if sockS is None:
		colorPrint(YELLOW, "*** creating send socket ***")
		sockS = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
		sockS.setsockopt(socket.IPPROTO_IP, socket.IP_MULTICAST_TTL, 2)
		sockS.setsockopt(socket.IPPROTO_IP, socket.IP_MULTICAST_LOOP, 0)

		if join:
			multicastJoin(sockS, groupAddr)
	time.sleep(sleepTime)
	i=0
	while count>0:
		cnt = '_' + str(i)
		if i <= 1:
			cnt=''
		colorPrint(GREEN, "send to   "  + groupAddr  + ":" + str(groupPort) +  " > \"" + message + cnt + "\"")
		sockS.sendto(bytes(message + cnt,'utf-8'), (groupAddr, groupPort))
		count-=1
		i+=1
		time.sleep(sleepTime)

def multicastAlternate(groupAddr, groupPort, join, count, message, sleepTime):
	i=0
	while count>0:
		multicastSend(groupAddr, groupPort, join, 1, message + '_' + str(i), sleepTime)
		multicastRecv(groupAddr, groupPort, join, 1)
		time.sleep(sleepTime)
		count-=1
		i+=1

def restCreateGroup(groupAddr):
	import urllib3, json
	url = "http://127.0.0.1:8080/mc/group_create/json"
	encoded_body = json.dumps({"group_addr": groupAddr})
	print("creating a new multicast group:\n" + encoded_body + " -> " + str(url) )

	try:
		http = urllib3.PoolManager()
		res = http.request('POST',
			url, headers={'Content-Type': 'application/json'},
			body=encoded_body)
	except:
		print("Please, start FloodLight")
		print("Unexpected error:", sys.exc_info()[0])
		sys.exit(2)
	
	print("http status: " + str(res.status))
	print("http response: \"" + str(res.data,'utf-8') + "\"")

def restGroupInfo(groupAddr):
	import urllib3, json
	url = "http://127.0.0.1:8080/mc/group_info/json"
	encoded_body = json.dumps({"group_addr": groupAddr})

	try:
		http = urllib3.PoolManager()
		res = http.request('POST', url,
			headers={'Content-Type': 'application/json'},
			body=encoded_body)
	except:
		print("Please, start FloodLight")
		print("Unexpected error: ", sys.exc_info()[0])
		sys.exit(2)
	 
	print("http status: " + str(res.status))
	print("http response: " + str(res.data,'utf-8'))

def restGroupsInfo():
	import urllib3, json
	url = "http://127.0.0.1:8080/mc/groups_info/json"

	try:
		http = urllib3.PoolManager()
		res = http.request('GET', url)
	except:
		print("Please, start FloodLight")
		print("Unexpected error: ", sys.exc_info()[0])
		sys.exit(2)
	 
	print("http status: " + str(res.status))
	print("http response: " + str(res.data,'utf-8'))

def testNetwork(addr):
	try:
		from pyroute2 import IPRoute
	except:
		print("Please install python3-pyroute2")

	ip = IPRoute()
	try:
		ret = ip.get_routes(family=socket.AF_INET, dst=addr)
		ip.close()
		return True
	except:
		print("Network Unreachable")

	ip.close()
	return False

def defaultRoute():
	if testNetwork('239.1.1.1'):
		return
	print("adding default route")
	os.system('i=$(cat /proc/net/dev | grep eth | awk \'{print substr($1, 1, length($1)-1)}\') ; route add -net 0.0.0.0/32 dev $i; sysctl net.ipv4.icmp_echo_ignore_broadcasts=0')
	testNetwork('239.1.1.1')

def help():
	print("<HELP>")
	print("DEFAULT:\n\t239.1.1.1:5000, join=True, message=\"default_message\", count=1\n")
	print("PARAMETERS:")
	print("\t-g --group=    : [PAR] group IPv4 addres")
	print("\t-p --port=     : [PAR] group port")
	print("\t-c --create    : [ACT] rest HTTP create group")
	print("\t-I --groupinfo : [ACT] rest HTTP info about an existing group")
	print("\t-i --groupsinfo: [ACT] rest HTTP info about existing groups")
	print("\t-j --join=     : [OPT] flag to send IGMP join packet or not")
	print("\t-r --recv      : [ACT] receives for $count times")
	print("\t-s --send      : [ACT] send $msg for $count times")
	print("\t-m --message=  : [PAR] parameter for send")
	print("\t-C --count=    : [PAR] repeat action for $count times")
	print("\t-a --alternate : [ACT] alternate send/recv of $msg for $count times")

	print("\nEXAMPLES:")
	print("\tinfo   : ./multicast-test.py --info")
	print("\tcreate : ./multicast-test.py -g 239.1.1.1 -p 5000 --create")
	print("\trecv   : ./multicast-test.py -g 239.1.1.1 -p 5000 --recv --count 4")
	print("\tsend   : ./multicast-test.py -g 239.1.1.1 -p 5000 --send --message \"prova\" --count 4")

def main(argv):
	import getopt

	action=None
	groupAddr='239.1.1.1'
	groupPort=5000
	joinFlag=True
	message="default_message"
	count=1
	sleepTime=2

	try:
		opts, args = getopt.getopt(argv, "hg:p:cjrsm:iI:C:aS:", ["help", "group=", "port=", "create", "join=", "recv", "send", "message=", "groupinfo", "groupsinfo=", "count", "alternate","sleep="])
	except getopt.GetoptError:
		help()
		sys.exit(2)
	for opt, arg in opts:
		if opt in ("-h", "--help"):
			help()
			sys.exit(0)
		elif opt in ("-g", "--group"):
			groupAddr = arg
		elif opt in ("-p", "--port"):
			groupPort = int(arg)
		elif opt in ("-c", "--create"):
			action = 'c'
		elif opt in ("-j", "--join"):
			joinFlag = bool(opt)
		elif opt in ("-r", "--recv"):
			action = 'r'
		elif opt in ("-s", "--send"):
			action = 's'		
		elif opt in ("-m", "--message"):
			message = arg		
		elif opt in ("-i", "--groupsinfo"):
			action = 'i'
		elif opt in ("-I", "--groupinfo"):
			action = 'I'
		elif opt in ("-C", "--count"):
			count = int(arg)
		elif opt in ("-S", "--sleep"):
			SLEEP_TIME = int(arg)
		elif opt in ("-a", "--alternate"):
			action = 'a'
		else:
			help()
			sys.exit(2)

	defaultRoute()

	if action == 'c' and not (groupAddr is None):
		restCreateGroup(groupAddr)
	elif action == 'r' and (not None in [groupAddr,groupPort]):
		print("recv...")
		multicastRecv(groupAddr, groupPort, joinFlag, count)
	elif action == 's' and (not None in [groupAddr,groupPort]):
		print("send...")
		multicastSend(groupAddr, groupPort, joinFlag, count, message, sleepTime)
	elif action == 'i':
		restGroupsInfo()
	elif action == 'I':
		restGroupInfo(groupAddr)
	elif action == 'a' and (not None in [groupAddr,groupPort]):
		print("send/recv...")
		multicastAlternate(groupAddr, groupPort, joinFlag, count, message, sleepTime)
	else:
		help()
		sys.exit(2)

if __name__ == "__main__":
	main(sys.argv[1:])
