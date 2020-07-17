# this file is an altered version of app.py,
# here, the whole http request part has been stripped out
# and the way to input a file is to simply supply the full path of the file
# as the first argument on the command line

import hashlib
import sys
import os

import json

from flask import jsonify
from RiskInDroid import RiskInDroid


def main():

	file_path = sys.argv[1]

	# file_path = os.path.join(
	#	application.config["UPLOAD_DIR"],
	# 	"{0}_{1}".format(time.strftime("%H-%M-%S_%d-%m-%Y"), filename),
	# )
	# file.save(file_path)

	rid = RiskInDroid()

	permissions = rid.get_permission_json(file_path)

	try:
		response = {
			"name": file_path,
			"md5": md5sum(file_path),
			"risk": round(
				rid.calculate_risk(rid.get_feature_vector_from_json(permissions)),
				3,
			),
			"permissions": [
				val
				for val in list(
					map(
						lambda x: {"cat": "Declared", "name": x},
						permissions["declared"],
					)
				)
				+ list(
					map(
						lambda x: {"cat": "Required and Used", "name": x},
						permissions["requiredAndUsed"],
					)
				)
				+ list(
					map(
						lambda x: {"cat": "Required but Not Used", "name": x},
						permissions["requiredButNotUsed"],
					)
				)
				+ list(
					map(
						lambda x: {"cat": "Not Required but Used", "name": x},
						permissions["notRequiredButUsed"],
					)
				)
			],
		}
		print(json.dumps(response))
	except Exception:
		raise Exception("The provided file is not valid : '" + sys.argv[1] + "'")
	


def md5sum(file_path, block_size=65536):
	md5_hash = hashlib.md5()
	with open(file_path, "rb") as filename:
		for chunk in iter(lambda: filename.read(block_size), b""):
			md5_hash.update(chunk)
	return md5_hash.hexdigest()
	
	
	
if __name__ == "__main__":
    main()